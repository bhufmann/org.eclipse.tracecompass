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

```java
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
```

### Match events from UST traces

Suppose a client-server application is instrumented using LTTng-UST.
Traces are collected on the server and some clients on different
machines. The traces can be synchronized using network event matching.

The following metadata describes the events:

```
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
```

One would need to write an event match definition for those 2 events as
follows:

```java
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
```

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
