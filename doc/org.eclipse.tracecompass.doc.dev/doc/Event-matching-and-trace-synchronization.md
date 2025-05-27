# Event matching and trace synchronization

Event matching consists in taking an event from a trace and linking it
to another event in a possibly different trace. The example that comes
to mind is matching network packets sent from one traced machine to
another traced machine. These matches can be used to synchronize traces.

Trace synchronization consists in taking traces, taken on different
machines, with a different time reference, and finding the formula to
transform the timestamps of some of the traces, so that they all have
the same time reference.

## Event matching interfaces

Here's a description of the major parts involved in event matching.
These classes are all in the
*org.eclipse.tracecompass.tmf.core.event.matching* package:

- **ITmfEventMatching**: Controls the event matching process
- **ITmfMatchEventDefinition**: Describes how events are matched
- **IMatchProcessingUnit**: Processes the matched events

## Implementation details and how to extend it

### ITmfEventMatching interface and derived classes

This interface controls the event matching itself. Their only public
method is *matchEvents*. The implementing classes needs to manage how to
setup the traces, and any initialization or finalization procedures.

The is one concrete implementation of this interface:
**TmfEventMatching**. It makes a request on the traces and match events
where a *cause* event can be uniquely matched with a *effect* event. It
creates a **TmfEventDependency** between the source and destination
events. The dependency is added to the processing unit.

To match events requiring other mechanisms (for instance, a series of
events can be matched with another series of events), one would need to
add another class either implementing **ITmfEventMatching**. It would
most probably also require a new **ITmfMatchEventDefinition**
implementation.

### ITmfMatchEventDefinition interface and its derived classes

These are the classes that describe how to actually match specific
events together.

The **canMatchTrace** method will tell if a definition is compatible
with a given trace.

The **getEventKey** method will return a key for an event that uniquely
identifies this event and will match the key from another event.

The **getDirection** method indicates whether this event is a *cause* or
*effect* event to be matched with one from the opposite direction.

As examples, two concrete network match definitions have been
implemented in the
*org.eclipse.tracecompass.internal.lttng2.kernel.core.event.matching*
package for two compatible methods of matching TCP packets (See the
Trace Compass User Guide on *trace synchronization* for information on
those matching methods). Each one tells which events need to be present
in the metadata of a CTF trace for this matching method to be
applicable. It also returns the field values from each event that will
uniquely match 2 events together.

Each **IMatchEventDefinition** needs to be registered to the
**TmfEventMatching** class using the following code for example

    TmfEventMatching.registerMatchObject(new TcpEventMatching());

### IMatchProcessingUnit interface and derived classes

While matching events is an exercise in itself, it's what to do with the
match that really makes this functionality interesting. This is the job
of the **IMatchProcessingUnit** interface.

**TmfEventMatches** provides a default implementation that only stores
the matches to count them. When a new match is obtained, the *addMatch*
is called with the match and the processing unit can do whatever needs
to be done with it.

A match processing unit can be an analysis in itself. For example, trace
synchronization is done through such a processing unit. One just needs
to set the processing unit in the TmfEventMatching constructor.

## Code examples

### Using network packets matching in an analysis

This example shows how one can create a processing unit inline to create
a link between two events. In this example, the code already uses an
event request, so there is no need here to call the *matchEvents*
method, that will only create another request.

    class MyAnalysis extends TmfAbstractAnalysisModule {

        private TmfNetworkEventMatching tcpMatching;

        ...

        protected void executeAnalysis() {

            IMatchProcessingUnit matchProcessing = new IMatchProcessingUnit() {
                @Override
                public void matchingEnded() {
                }

                @Override
                public void init(ITmfTrace[] fTraces) {
                }

                @Override
                public int countMatches() {
                    return 0;
                }

                @Override
                public void addMatch(TmfEventDependency match) {
                    log.debug("we got a tcp match! " + match.getSourceEvent().getContent() + " " + match.getDestinationEvent().getContent());
                    TmfEvent source = match.getSourceEvent();
                    TmfEvent destination = match.getDestinationEvent();
                    /* Create a link between the two events */
                }
            };

            ITmfTrace[] traces = { getTrace() };
            tcpMatching = new TmfEventMatching(traces, matchProcessing);
            tcpMatching.initMatching();

            MyEventRequest request = new MyEventRequest(this, i);
            getTrace().sendRequest(request);
        }

        public void analyzeEvent(TmfEvent event) {
            ...
            tcpMatching.matchEvent(event, 0);
            ...
        }

        ...

    }

    class MyEventRequest extends TmfEventRequest {

        private final MyAnalysis analysis;

        MyEventRequest(MyAnalysis analysis, int traceno) {
            super(CtfTmfEvent.class,
                TmfTimeRange.ETERNITY,
                0,
                TmfDataRequest.ALL_DATA,
                ITmfDataRequest.ExecutionType.FOREGROUND);
            this.analysis = analysis;
        }

        @Override
        public void handleData(final ITmfEvent event) {
            super.handleData(event);
            if (event != null) {
                analysis.analyzeEvent(event);
            }
        }
    }

### Match events from UST traces

Suppose a client-server application is instrumented using LTTng-UST.
Traces are collected on the server and some clients on different
machines. The traces can be synchronized using network event matching.

The following metadata describes the events:

        event {
            name = "myapp:send";
            id = 0;
            stream_id = 0;
            loglevel = 13;
            fields := struct {
                integer { size = 32; align = 8; signed = 1; encoding = none; base = 10; } _sendto;
                integer { size = 64; align = 8; signed = 1; encoding = none; base = 10; } _messageid;
                integer { size = 64; align = 8; signed = 1; encoding = none; base = 10; } _data;
            };
        };

        event {
            name = "myapp:receive";
            id = 1;
            stream_id = 0;
            loglevel = 13;
            fields := struct {
                integer { size = 32; align = 8; signed = 1; encoding = none; base = 10; } _from;
                integer { size = 64; align = 8; signed = 1; encoding = none; base = 10; } _messageid;
                integer { size = 64; align = 8; signed = 1; encoding = none; base = 10; } _data;
            };
        };

One would need to write an event match definition for those 2 events as
follows:

    public class MyAppUstEventMatching implements ITmfMatchEventDefinition {

        public class MyEventMatchingKey implements IEventMatchingKey {

            private static final HashFunction HF = checkNotNull(Hashing.goodFastHash(32));
            private final int fTo;
            private final long fId;

            public MyEventMatchingKey(int to, long id) {
                fTo = to;
                fId = id;
            }

            @Override
            public int hashCode() {
                return HF.newHasher()
                    .putInt(fTo)
                    .putLong(fId).hash().asInt();
            }

            @Override
            public boolean equals(@Nullable Object o) {
                if (o instanceof MyEventMatchingKey) {
                    MyEventMatchingKey key = (MyEventMatchingKey) o;
                    return (key.fTo == fTo &&
                        key.fId == fId);
                }
                return false;
            }
        }


        @Override
        public Direction getDirection(ITmfEvent event) {
            String evname = event.getType().getName();
            if (evname.equals("myapp:receive")) {
                return Direction.EFFECT;
            } else if (evname.equals("myapp:send")) {
                return Direction.CAUSE;
            }
            return null;
        }

        @Override
        public IEventMatchingKey getEventKey(ITmfEvent event) {
            IEventMatchingKey key;

            if (evname.equals("myapp:receive")) {
                key = new MyEventMatchingKey(event.getContent().getField("from").getValue(),
                    event.getContent().getField("messageid").getValue());
            } else {
                key = new MyEventMatchingKey(event.getContent().getField("sendto").getValue(),
                    event.getContent().getField("messageid").getValue());
            }

            return key;
        }

        @Override
        public boolean canMatchTrace(ITmfTrace trace) {
            Set<String> events = ImmutableSet.of("myapp:receive", "myapp:send");
            if (!(trace instanceof ITmfTraceWithPreDefinedEvents)) {
                return false;
            }
            ITmfTraceWithPreDefinedEvents ktrace = (ITmfTraceWithPreDefinedEvents) trace;

            Set<String> traceEvents = TmfEventTypeCollectionHelper.getEventName(ktrace.getContainedEventTypes());
            traceEvents.retainAll(events);
            return !traceEvents.isEmpty();
        }

    }

The following code will have to be run before the trace synchronization
takes place, for example in the Activator of the plugin:

    TmfEventMatching.registerMatchObject(new MyAppUstEventMatching());

Now, only adding the traces in an experiment and clicking the
**Synchronize traces** menu item will synchronize the traces using the
new definition for event matching.

## Trace synchronization

Trace synchronization classes and interfaces are located in the
*org.eclipse.tracecompass.tmf.core.synchronization* package.

### Synchronization algorithm

Synchronization algorithms are used to synchronize traces from events
matched between traces. After synchronization, traces taken on different
machines with different time references see their timestamps modified
such that they all use the same time reference (typically, the time of
at least one of the traces). With traces from different machines, it is
impossible to have perfect synchronization, so the result is a best
approximation that takes network latency into account.

The abstract class **SynchronizationAlgorithm** is a processing unit for
matches. New synchronization algorithms must extend this one, it already
contains the functions to get the timestamp transforms for different
traces.

The *fully incremental convex hull* synchronization algorithm is the
default synchronization algorithm.

While the synchronization system provisions for more synchronization
algorithms, there is not yet a way to select one, the experiment's trace
synchronization uses the default algorithm. To test a new
synchronization algorithm, the synchronization should be called directly
like this:

    SynchronizationAlgorithm syncAlgo = new MyNewSynchronizationAlgorithm();
    syncAlgo = SynchronizationManager.synchronizeTraces(syncFile, traces, syncAlgo, true);

### Timestamp transforms

Timestamp transforms are the formulae used to transform the timestamps
from a trace into the reference time. The **ITmfTimestampTransform** is
the interface to implement to add a new transform.

The following classes implement this interface:

- **TmfTimestampTransform**: default transform. It cannot be
  instantiated, it has a single static object
  *TmfTimestampTransform.IDENTITY*, which returns the original
  timestamp.
- **TmfConstantTransform**: simply applies an offset to the timestamp,
  so the formula would be: *f(t) = t + c* where *c* is the offset to
  apply.
- **TmfTimestampTransformLinear**: transforms the timestamp using a
  linear formula: *f(t) = at + b*, where *a* and *b* are computed by the
  synchronization algorithm.

These classes are not accessible directly, to create any timestamp
transform, one needs to use one of the methods from the
**TimestampTransformFactory** utility class.

One could extend the interface for other timestamp transforms, for
instance to have a transform where the formula would change over the
course of the trace.

## Todo

Here's a list of features not yet implemented that would enhance trace
synchronization and event matching:

- Ability to select a synchronization algorithm
- Implement the minimum spanning tree algorithm by Masoume Jabbarifar
  (article on the subject not published yet) to automatically select the
  best reference trace
- Instead of having the timestamp transforms per trace, have the
  timestamp transform as part of an experiment context, so that the
  trace's specific analysis, like the state system, are in the original
  trace, but are transformed only when needed for an experiment
  analysis.
- Add more views to display the synchronization information (only
  textual statistics are available for now)

# Analysis Framework

Analysis modules are useful to tell the user exactly what can be done
with a trace. The analysis framework provides an easy way to access and
execute the modules and open the various outputs available.

Analyses can have parameters they can use in their code. They also have
outputs registered to them to display the results from their execution.

## Creating a new module

All analysis modules must implement the **IAnalysisModule** interface
from the o.e.l.tmf.core project. An abstract class,
**TmfAbstractAnalysisModule**, provides a good base implementation. It
is strongly suggested to use it as a superclass of any new analysis.

### Example

This example shows how to add a simple analysis module for an LTTng
kernel trace with two parameters. It also specifies two mandatory events
by overriding **getAnalysisRequirements**. The analysis requirements are
further explained in the section
<a href="#Providing_requirements_to_analyses" class="wikilink"
title="#Providing requirements to analyses">#Providing requirements to
analyses</a>.

    public class MyLttngKernelAnalysis extends TmfAbstractAnalysisModule {

        public static final String PARAM1 = "myparam";
        public static final String PARAM2 = "myotherparam";

        @Override
        public Iterable<TmfAnalysisRequirement> getAnalysisRequirements() {

            // initialize the requirement: events
            Set<@NonNull String> requiredEvents = ImmutableSet.of("sched_switch", "sched_wakeup");
            TmfAbstractAnalysisRequirement eventsReq = new TmfAnalysisEventRequirement(requiredEvents, PriorityLevel.MANDATORY);

            return ImmutableList.of(eventsReq);
        }

        @Override
        protected void canceling() {
    	     /* The job I am running in is being cancelled, let's clean up */
        }

        @Override
        protected boolean executeAnalysis(final IProgressMonitor monitor) {
            /*
             * I am running in an Eclipse job, and I already know I can execute
             * on a given trace.
             *
             * In the end, I will return true if I was successfully completed or
             * false if I was either interrupted or something wrong occurred.
             */
            Object param1 = getParameter(PARAM1);
            int param2 = (Integer) getParameter(PARAM2);
        }

        @Override
        public Object getParameter(String name) {
            Object value = super.getParameter(name);
            /* Make sure the value of param2 is of the right type. For sake of
               simplicity, the full parameter format validation is not presented
               here */
            if ((value != null) && name.equals(PARAM2) && (value instanceof String)) {
                return Integer.parseInt((String) value);
            }
            return value;
        }

    }

### Available base analysis classes and interfaces

The following are available as base classes for analysis modules. They
also extend the abstract **TmfAbstractAnalysisModule**

- **TmfStateSystemAnalysisModule**: A base analysis module that builds
  one state system. A module extending this class only needs to provide
  a state provider and the type of state system backend to use. All
  state systems should now use this base class as it also contains all
  the methods to actually create the state sytem with a given backend.
  An example of this kind of analysis module can be found in
  <a href="#Analysis_module_definition" class="wikilink"
  title="#Analysis module definition">#Analysis module definition</a>.

The following interfaces can optionally be implemented by analysis
modules if they use their functionalities. For instance, some utility
views, like the State System Explorer, may have access to the module's
data through these interfaces.

- **ITmfAnalysisModuleWithStateSystems**: Modules implementing this have
  one or more state systems included in them. For example, a module may
  "hide" 2 state system modules for its internal workings. By
  implementing this interface, it tells that it has state systems and
  can return them if required.

### How it works

Analyses are managed through the **TmfAnalysisManager**. The analysis
manager is a singleton in the application and keeps track of all
available analysis modules, with the help of **IAnalysisModuleHelper**.
It can be queried to get the available analysis modules, either all of
them or only those for a given tracetype. The helpers contain the
non-trace specific information on an analysis module: its id, its name,
the tracetypes it applies to, etc.

When a trace is opened, the helpers for the applicable analysis create
new instances of the analysis modules. The analyses are then kept in a
field of the trace and can be executed automatically or on demand.

The analysis is executed by calling the **IAnalysisModule#schedule()**
method. This method makes sure the analysis is executed only once and,
if it is already running, it won't start again. The analysis itself is
run inside an Eclipse job that can be cancelled by the user or the
application. The developer must consider the progress monitor that comes
as a parameter of the **executeAnalysis()** method, to handle the proper
cancellation of the processing. The
**IAnalysisModule#waitForCompletion()** method will block the calling
thread until the analysis is completed. The method will return whether
the analysis was successfully completed or if it was cancelled.

A running analysis can be cancelled by calling the
**IAnalysisModule#cancel()** method. This will set the analysis as done,
so it cannot start again unless it is explicitly reset. This is done by
calling the protected method **resetAnalysis**.

## Telling TMF about the analysis module

Now that the analysis module class exists, it is time to hook it to the
rest of TMF so that it appears under the traces in the project explorer.
The way to do so is to add an extension of type
*org.eclipse.linuxtools.tmf.core.analysis* to a plugin, either through
the *Extensions* tab of the Plug-in Manifest Editor or by editing
directly the plugin.xml file.

The following code shows what the resulting plugin.xml file should look
like.

    <extension
             point="org.eclipse.linuxtools.tmf.core.analysis">
          <module
             id="my.lttng.kernel.analysis.id"
             name="My LTTng Kernel Analysis"
             analysis_module="my.plugin.package.MyLttngKernelAnalysis"
             applies_experiment="false"
             automatic="true">
             <parameter
                   name="myparam">
             </parameter>
             <parameter
                   default_value="3"
                   name="myotherparam" />
             <tracetype
                   class="org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace">
             </tracetype>
          </module>
    </extension>

This defines an analysis module where the *analysis_module* attribute
corresponds to the module class and must implement IAnalysisModule. This
module has 2 parameters: *myparam* and *myotherparam* which has default
value of 3. The *tracetype* element tells which tracetypes this analysis
applies to. There can be many tracetypes. Also, the *automatic*
attribute of the module indicates whether this analysis should be run
when the trace is opened, or wait for the user's explicit request.

Since experiments are traces too, it is possible to develop an analysis
to work on experiment by setting the *tracetype* to the experiment's ID
(the ID for the default experiment is
*org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment*).
Setting the *applies_experiment* attribute to **true** creates a new
instance of the analysis for an experiment that contains any trace of
the correct type. This analysis runs on the whole experiment and will
process all events. It is useful for analysis that are enhanced with
data from multiple traces.

Note that with these extension points, it is possible to use the same
module class for more than one analysis (with different ids and names).
That is a desirable behavior. For instance, a third party plugin may add
a new tracetype different from the one the module is meant for, but on
which the analysis can run. Also, different analyses could provide
different results with the same module class but with different default
values of parameters.

## Attaching outputs and views to the analysis module

Analyses will typically produce outputs the user can examine. Outputs
can be a text dump, a .dot file, an XML file, a view, etc. All output
types must implement the **IAnalysisOutput** interface.

An output can be registered to an analysis module at any moment by
calling the **IAnalysisModule#registerOutput()** method. Analyses
themselves may know what outputs are available and may register them in
the analysis constructor or after analysis completion.

The various concrete output types are:

- **TmfAnalysisViewOutput**: It takes a view ID as parameter and, when
  selected, opens the view.

### Using the extension point to add outputs

Analysis outputs can also be hooked to an analysis using the same
extension point *org.eclipse.linuxtools.tmf.core.analysis* in the
plugin.xml file. Outputs can be matched either to a specific analysis
identified by an ID, or to all analysis modules extending or
implementing a given class or interface.

The following code shows how to add a view output to the analysis
defined above directly in the plugin.xml file. This extension does not
have to be in the same plugin as the extension defining the analysis.
Typically, an analysis module can be defined in a core plugin, along
with some outputs that do not require UI elements. Other outputs, like
views, who need UI elements, will be defined in a ui plugin.

    <extension
             point="org.eclipse.linuxtools.tmf.core.analysis">
          <output
                class="org.eclipse.tracecompass.tmf.ui.analysis.TmfAnalysisViewOutput"
                id="my.plugin.package.ui.views.myView">
             <analysisId
                   id="my.lttng.kernel.analysis.id">
             </analysisId>
          </output>
          <output
                class="org.eclipse.tracecompass.tmf.ui.analysis.TmfAnalysisViewOutput"
                id="my.plugin.package.ui.views.myMoreGenericView">
             <analysisModuleClass
                   class="my.plugin.package.core.MyAnalysisModuleClass">
             </analysisModuleClass>
          </output>
    </extension>

## Providing help for the module

For now, the only way to provide a meaningful help message to the user
is by overriding the **IAnalysisModule#getHelpText()** method and return
a string that will be displayed in a message box.

What still needs to be implemented is for a way to add a full
user/developer documentation with mediawiki text file for each module
and automatically add it to Eclipse Help. Clicking on the Help menu item
of an analysis module would open the corresponding page in the help.

## Using analysis parameter providers

An analysis may have parameters that can be used during its execution.
Default values can be set when describing the analysis module in the
plugin.xml file, or they can use the **IAnalysisParameterProvider**
interface to provide values for parameters.
**TmfAbstractAnalysisParamProvider** provides an abstract implementation
of this interface, that automatically notifies the module of a parameter
change.

### Example parameter provider

The following example shows how to have a parameter provider listen to a
selection in the LTTng kernel Control Flow view and send the thread id
to the analysis.

    public class MyLttngKernelParameterProvider extends TmfAbstractAnalysisParamProvider {

        private ControlFlowEntry fCurrentEntry = null;

        private static final String NAME = "My Lttng kernel parameter provider"; //$NON-NLS-1$

        private ISelectionListener selListener = new ISelectionListener() {
            @Override
            public void selectionChanged(IWorkbenchPart part, ISelection selection) {
                if (selection instanceof IStructuredSelection) {
                    Object element = ((IStructuredSelection) selection).getFirstElement();
                    if (element instanceof ControlFlowEntry) {
                        ControlFlowEntry entry = (ControlFlowEntry) element;
                        setCurrentThreadEntry(entry);
                    }
                }
            }
        };

        /*
         * Constructor
         */
        public MyLttngKernelParameterProvider() {
            super();
            registerListener();
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Object getParameter(String name) {
            if (fCurrentEntry == null) {
                return null;
            }
            if (name.equals(MyLttngKernelAnalysis.PARAM1)) {
                return fCurrentEntry.getThreadId();
            }
            return null;
        }

        @Override
        public boolean appliesToTrace(ITmfTrace trace) {
            return (trace instanceof LttngKernelTrace);
        }

        private void setCurrentThreadEntry(ControlFlowEntry entry) {
            if (!entry.equals(fCurrentEntry)) {
                fCurrentEntry = entry;
                this.notifyParameterChanged(MyLttngKernelAnalysis.PARAM1);
            }
        }

        private void registerListener() {
            final IWorkbench wb = PlatformUI.getWorkbench();

            final IWorkbenchPage activePage = wb.getActiveWorkbenchWindow().getActivePage();

            /* Add the listener to the control flow view */
            view = activePage.findView(ControlFlowView.ID);
            if (view != null) {
                view.getSite().getWorkbenchWindow().getSelectionService().addPostSelectionListener(selListener);
                view.getSite().getWorkbenchWindow().getPartService().addPartListener(partListener);
            }
        }

    }

### Register the parameter provider to the analysis

To have the parameter provider class register to analysis modules, it
must first register through the analysis manager. It can be done in a
plugin's activator as follows:

    @Override
    public void start(BundleContext context) throws Exception {
        /* ... */
        TmfAnalysisManager.registerParameterProvider("my.lttng.kernel.analysis.id", MyLttngKernelParameterProvider.class)
    }

where **MyLttngKernelParameterProvider** will be registered to analysis
*"my.lttng.kernel.analysis.id"*. When the analysis module is created,
the new module will register automatically to the singleton parameter
provider instance. Only one module is registered to a parameter provider
at a given time, the one corresponding to the currently selected trace.

## Providing requirements to analyses

### Analysis requirement provider API

A requirement defines the needs of an analysis. For example, an analysis
could need an event named *"sched_switch"* in order to be properly
executed. The requirements are represented by extending the class
**TmfAbstractAnalysisRequirement**. Since **IAnalysisModule** extends
the **IAnalysisRequirementProvider** interface, all analysis modules
must provide their requirements. If the analysis module extends
**TmfAbstractAnalysisModule**, it has the choice between overriding the
requirements getter
(**IAnalysisRequirementProvider#getAnalysisRequirements()**) or not,
since the abstract class returns an empty collection by default (no
requirements).

### Requirement values

Each concrete analysis requirement class will define how a requirement
is verified on a given trace. When creating a requirement, the developer
will specify a set of values for that class. With an 'event' type
requirement, a trace generator like the LTTng Control could
automatically enable the required events. Another point we have to take
into consideration is the priority level when creating a requirement
object. The enum **TmfAbstractAnalysisRequirement#PriorityLevel** gives
the choice between **PriorityLevel#OPTIONAL**,
**PriorityLevel#ALL_OR_NOTHING**, **PriorityLevel#AT_LEAST_ONE** or
**PriorityLevel#MANDATORY**. That way, we can tell if an analysis can
run without a value or not.

To create a requirement one has the choice to extend the abstract class
**TmfAbstractAnalysisRequirement** or use the existing implementations:
**TmfAnalysisEventRequirement** (will verify the presence of events
identified by name), **TmfAnalysisEventFieldRequirement** (will verify
the presence of fields for some or all events) or
**TmfCompositeAnalysisRequirement** (join requirements together with one
of the priority levels).

Moreover, information can be added to requirements. That way, the
developer can explicitly give help details at the requirement level
instead of at the analysis level (which would just be a general help
text). To add information to a requirement, the method
**TmfAnalysisRequirement#addInformation()** must be called. Adding
information is not mandatory.

### Example of providing requirements

In this example, we will implement a method that initializes a
requirement object and return it in the
**IAnalysisRequirementProvider#getAnalysisRequirements()** getter. The
example method will return a set with three requirements. The first one
will indicate a mandatory event needed by a specific analysis, the
second one will tell an optional event name and the third will indicate
mandatory event fields for the given event type.

Note that in LTTng event contexts are considered as event fields. Using
the **TmfAnalysisEventFieldRequirement** it's possible to define
requirements on event contexts (see 3rd requirement in example below).

        @Override
        public @NonNull Iterable<@NonNull TmfAbstractAnalysisRequirement> getAnalysisRequirements() {

            /* Requirement on event name */
            Set<@NonNull String> requiredEvents = ImmutableSet.of("sched_wakeup");
            TmfAbstractAnalysisRequirement eventsReq1 = new TmfAnalysisEventRequirement(requiredEvents, PriorityLevel.MANDATORY);

            requiredEvents = ImmutableSet.of("sched_switch");
            TmfAbstractAnalysisRequirement eventsReq2 = new TmfAnalysisEventRequirement(requiredEvents, PriorityLevel.OPTIONAL);

            /* An information about the events */
            eventsReq2.addInformation("The event sched_wakeup is optional because it's not properly handled by this analysis yet.");

            /* Requirement on event fields */
            Set<@NonNull String> requiredEventFields = ImmutableSet.of("context._procname", "context._ip");
            TmfAbstractAnalysisRequirement eventFieldRequirement = new TmfAnalysisEventFieldRequirement(
                     "event name",
                     requiredEventFields,
                     PriorityLevel.MANDATORY);

             Set<TmfAbstractAnalysisRequirement> requirements = ImmutableSet.of(eventsReq1, eventsReq2, eventFieldRequirement);
             return requirements;
        }

## TODO

Here's a list of features not yet implemented that would improve the
analysis module user experience:

- Implement help using the Eclipse Help facility (without forgetting an
  eventual command line request)
- The abstract class **TmfAbstractAnalysisModule** executes an analysis
  as a job, but nothing compels a developer to do so for an analysis
  implementing the **IAnalysisModule** interface. We should force the
  execution of the analysis as a job, either from the trace itself or
  using the TmfAnalysisManager or by some other mean.
- Views and outputs are often registered by the analysis themselves
  (forcing them often to be in the .ui packages because of the views),
  because there is no other easy way to do so. We should extend the
  analysis extension point so that .ui plugins or other third-party
  plugins can add outputs to a given analysis that resides in the core.
- Improve the user experience with the analysis:
  - Allow the user to select which analyses should be available, per
    trace or per project.
  - Allow the user to view all available analyses even though he has no
    imported traces.
  - Allow the user to generate traces for a given analysis, or generate
    a template to generate the trace that can be sent as parameter to
    the tracer.
  - Give the user a visual status of the analysis: not executed, in
    progress, completed, error.
  - Give a small screenshot of the output as icon for it.
  - Allow to specify parameter values from the GUI.
- Add the possibility for an analysis requirement to be composed of
  another requirement.
- Generate a trace session from analysis requirements.
