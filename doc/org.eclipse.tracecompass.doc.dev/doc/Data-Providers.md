<!--TOC-->

- [Data Providers](#data-providers)
  - [Data provider types](#data-provider-types)
    - [XY](#xy)
    - [Time Graph](#time-graph)
  - [Data provider management](#data-provider-management)
    - [Data Provider Factories](#data-provider-factories)
    - [Extension point](#extension-point)
    - [Experiments](#experiments)
  - [Utilities](#utilities)
  - [Views](#views)
    - [XY views](#xy-views)
    - [Time Graph Views](#time-graph-views)

<!--TOC-->

# Data Providers

Starting in Trace Compass 3.3, the core and UI are being decoupled with
the data provider interface. This interface aims to provide a standard
data model for different types of views.

Data providers are queried with a filter object, which usually contains
a time range as well as other parameters required to correctly filter
and sample the returned data. They also take an optional progress
monitor to cancel the task. The returned models are encapsulated in a
**TmfModelResponse** object, which is generic (to the response's type)
and also encapsulates the Status of the reponse:

- CANCELLED if the query was cancelled by the progress monitor
- FAILED if an error occurred inside the data provider
- RUNNING if the response was returned before the underlying analysis
  was completed, and querying the provider again with the same
  parameters can return a different model.
- COMPLETED if the underlying analysis is finished and we do not expect
  a different response for the query parameters.

*Note that a complete example of analysis, data provider and views can
be found in the [org.eclipse.tracecompass.examples plugin
sources](https://github.com/eclipse-tracecompass/org.eclipse.tracecompass/blob/master/doc/org.eclipse.tracecompass.examples).*

## Data provider types

The base data provider **ITmfDataProvider** interface is the interface
each data provider type has to implement. The data provider factories
create instances of such provider type. Tree data providers are of type
**ITmfTreeDataProvider** extend the **ITmfDataProvider** interface and
returns a tree, as a list of **TmfTreeDataModel**, with a name, ID and
parent ID. The ID is unique to a provider type and the parent ID
indicates which element from the list is the entry's parent to rebuild
the tree hierarchy from the list of models. Note such tree need to have
limited size to not exceed the available memory.

The base **TimeGraphEntryModel** class extends this with a start time
and end time. Concrete classes are free to add other fields, which can
be used in the Eclipse Trace Context where the UI code can access the
model type. However, in Trace Compass server context only fields defined
in the Trace Server protocol will be serialized.

### XY

The XY data provider type is used to associate an XY series to an entry
from the tree. The data provider is queried with a filter that also
contains a Collection of the IDs of the entries for which we want XY
series. The response contains a map of the series for the desired IDs.

Each XY series can have its own x axis (**ISeriesModel** /
**SeriesModel** - encapsulated in an **ITmfXyModel** / **TmfXyModel**)
or they can be shared by all models (**IYModel** / **YModel**
encapsulated in an **ITmfCommonXAxisModel** / **TmfCommonXAxisModel**).
The X axis is an array of longs, which makes it useful for a time axis
or time buckets, but it can be used for any XY content.

The interface to implement is **ITmfTreeXYDataProvider**. Abstract base
classes exist for common use case, e.g. **AbstractTreeDataProvider** for
tree data providers that are using a state system. Extend those classes
if applicable.

Here is a simple example of XY data provider, retrieving data from a
simple state system displaying the child attributes of the root
attributes.

```java
    import java.util.ArrayList;
    import java.util.Collection;
    import java.util.Collections;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Map;
    import java.util.Map.Entry;
    import java.util.concurrent.atomic.AtomicLong;

    import org.eclipse.core.runtime.IProgressMonitor;
    import org.eclipse.jdt.annotation.NonNull;
    import org.eclipse.jdt.annotation.NonNullByDefault;
    import org.eclipse.jdt.annotation.Nullable;
    import org.eclipse.tracecompass.examples.core.analysis.ExampleStateSystemAnalysisModule;
    import org.eclipse.tracecompass.internal.tmf.core.model.tree.AbstractTreeDataProvider;
    import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
    import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
    import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
    import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
    import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
    import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
    import org.eclipse.tracecompass.tmf.core.model.TmfCommonXAxisModel;
    import org.eclipse.tracecompass.tmf.core.model.YModel;
    import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
    import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
    import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
    import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
    import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
    import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;
    import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
    import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
    import org.eclipse.tracecompass.tmf.core.response.ITmfResponse.Status;
    import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
    import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
    import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

    import com.google.common.collect.BiMap;
    import com.google.common.collect.HashBiMap;

    /**
     * An example of an XY data provider.
     *
     * @author Genevi�ve Bastien
     */
    @SuppressWarnings("restriction")
    @NonNullByDefault
    public class ExampleXYDataProvider extends AbstractTreeDataProvider<ExampleStateSystemAnalysisModule, TmfTreeDataModel> implements ITmfTreeXYDataProvider<TmfTreeDataModel> {

        /**
         * Provider unique ID.
         */
        public static final String ID = "org.eclipse.tracecompass.examples.xy.dataprovider"; //$NON-NLS-1$
        private static final AtomicLong sfAtomicId = new AtomicLong();

        private final BiMap<Long, Integer> fIDToDisplayQuark = HashBiMap.create();

        /**
         * Constructor
         *
         * @param trace
         *            The trace this data provider is for
         * @param analysisModule
         *            The analysis module
         */
        public ExampleXYDataProvider(ITmfTrace trace, ExampleStateSystemAnalysisModule analysisModule) {
            super(trace, analysisModule);
        }

        /**
         * Create the time graph data provider
         *
         * @param trace
         *            The trace for which is the data provider
         * @return The data provider
         */
        public static @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> create(ITmfTrace trace) {
            ExampleStateSystemAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, ExampleStateSystemAnalysisModule.class, ExampleStateSystemAnalysisModule.ID);
            return module != null ? new ExampleXYDataProvider(trace, module) : null;
        }


        @Override
        public String getId() {
            return ID;
        }

        @Override
        protected boolean isCacheable() {
            return true;
        }

        @Override
        protected TmfTreeModel<TmfTreeDataModel> getTree(ITmfStateSystem ss, Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
            // Make an entry for each base quark
            List<TmfTreeDataModel> entryList = new ArrayList<>();
            for (Integer quark : ss.getQuarks("CPUs", "*")) { //$NON-NLS-1$ //$NON-NLS-2$
                int statusQuark = ss.optQuarkRelative(quark, "Status"); //$NON-NLS-1$
                if (statusQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                    Long id = fIDToDisplayQuark.inverse().computeIfAbsent(statusQuark, q -> sfAtomicId.getAndIncrement());
                    entryList.add(new TmfTreeDataModel(id, -1, ss.getAttributeName(quark)));
                }
            }
            return new TmfTreeModel<>(Collections.emptyList(), entryList);
        }

        @Override
        public TmfModelResponse<ITmfXyModel> fetchXY(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
            ITmfStateSystem ss = getAnalysisModule().getStateSystem();
            if (ss == null) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.ANALYSIS_INITIALIZATION_FAILED);
            }

            Map<Integer, double[]> quarkToValues = new HashMap<>();
            // Prepare the quarks to display
            Collection<Long> selectedItems = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
            if (selectedItems == null) {
                // No selected items, take them all
                selectedItems = fIDToDisplayQuark.keySet();
            }
            List<Long> times = getTimes(ss, DataProviderParameterUtils.extractTimeRequested(fetchParameters));
            for (Long id : selectedItems) {
                Integer quark = fIDToDisplayQuark.get(id);
                if (quark != null) {
                    quarkToValues.put(quark, new double[times.size()]);
                }
            }
            long[] nativeTimes = new long[times.size()];
            for (int i = 0; i < times.size(); i++) {
                nativeTimes[i] = times.get(i);
            }

            // Query the state system to fill the array of values
            try {
                for (ITmfStateInterval interval : ss.query2D(quarkToValues.keySet(), times)) {
                    if (monitor != null && monitor.isCanceled()) {
                        return new TmfModelResponse<>(null, Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
                    }
                    double[] row = quarkToValues.get(interval.getAttribute());
                    Object value = interval.getValue();
                    if (row != null && (value instanceof Number)) {
                        Double dblValue = ((Number) value).doubleValue();
                        for (int i = 0; i < times.size(); i++) {
                            Long time = times.get(i);
                            if (interval.getStartTime() <= time && interval.getEndTime() >= time) {
                                row[i] = dblValue;
                            }
                        }
                    }
                }
            } catch (IndexOutOfBoundsException | TimeRangeException | StateSystemDisposedException e) {
                return new TmfModelResponse<>(null, Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
            }
            List<IYModel> models = new ArrayList<>();
            for (Entry<Integer, double[]> values : quarkToValues.entrySet()) {
                models.add(new YModel(fIDToDisplayQuark.inverse().getOrDefault(values.getKey(), -1L), values.getValue()));
            }

            return new TmfModelResponse<>(new TmfCommonXAxisModel("Example XY data provider", nativeTimes, models), Status.COMPLETED, CommonStatusMessage.COMPLETED); //$NON-NLS-1$
        }

        private static List<Long> getTimes(ITmfStateSystem key, @Nullable List<Long> list) {
            if (list == null) {
                return Collections.emptyList();
            }
            List<@NonNull Long> times = new ArrayList<>();
            for (long t : list) {
                if (key.getStartTime() <= t && t <= key.getCurrentEndTime()) {
                    times.add(t);
                }
            }
            Collections.sort(times);
            return times;
        }

    }
```

### Time Graph

The Time Graph data provider is used to associate states to tree
entries, i.e. a sampled list of states, with a start time, duration,
integer value and optional label. The time graph states
(**ITimeGraphState** / **TimeGraphState**) are encapsulated in an
**ITimeGraphRowModel** which also provides the ID of the entry they map
to.

The time graph data provider can also supply arrows to link entries one
to another with a start time and start ID as well as a duration and
target ID. The interface to implement is **ITimeGraphArrow**, else
**TimeGraphArrow** can be extended.

Additional information can be added to the states with tooltips, which
are maps of tooltip entry names to tooltip entry values. The data
provider may also suggest styles for the states by implementing the
**IOutputStyleProvider** interface.

The interface to implement is **ITimeGraphDataProvider**.

Also, if the data provider wants to provide some styling information,
for example, colors, height and opacity, etc, it can implement the
**IOutputStyleProvider** interface who will add a method to fetch
styles. The **TimeGraphState** objects can then be constructed with a
style and the view will automatically use this style information.

Here is a simple example of a time graph data provider, retrieving data
from a simple state system where each root attribute is to be displayed.
It also provides simple styling.

```java
    import java.util.ArrayList;
    import java.util.Collection;
    import java.util.Collections;
    import java.util.Comparator;
    import java.util.HashMap;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;
    import java.util.concurrent.atomic.AtomicLong;
    import java.util.function.Predicate;

    import org.eclipse.core.runtime.IProgressMonitor;
    import org.eclipse.jdt.annotation.NonNull;
    import org.eclipse.jdt.annotation.NonNullByDefault;
    import org.eclipse.jdt.annotation.Nullable;
    import org.eclipse.tracecompass.examples.core.analysis.ExampleStateSystemAnalysisModule;
    import org.eclipse.tracecompass.internal.tmf.core.model.AbstractTmfTraceDataProvider;
    import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
    import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
    import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
    import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
    import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
    import org.eclipse.tracecompass.tmf.core.dataprovider.X11ColorUtils;
    import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
    import org.eclipse.tracecompass.tmf.core.model.IOutputStyleProvider;
    import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
    import org.eclipse.tracecompass.tmf.core.model.OutputStyleModel;
    import org.eclipse.tracecompass.tmf.core.model.StyleProperties;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphEntryModel;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
    import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
    import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
    import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
    import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
    import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
    import org.eclipse.tracecompass.tmf.core.response.ITmfResponse.Status;
    import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
    import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
    import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

    import com.google.common.collect.BiMap;
    import com.google.common.collect.HashBiMap;
    import com.google.common.collect.ImmutableMap;
    import com.google.common.collect.Multimap;

    /**
     * An example of a time graph data provider.
     *
     * @author Genevi�ve Bastien
     */
    @SuppressWarnings("restriction")
    @NonNullByDefault
    public class ExampleTimeGraphDataProvider extends AbstractTmfTraceDataProvider implements ITimeGraphDataProvider<@NonNull ITimeGraphEntryModel>, IOutputStyleProvider {

        /**
         * Provider unique ID.
         */
        public static final String ID = "org.eclipse.tracecompass.examples.timegraph.dataprovider"; //$NON-NLS-1$
        private static final AtomicLong sfAtomicId = new AtomicLong();
        private static final String STYLE0_NAME = "style0"; //$NON-NLS-1$
        private static final String STYLE1_NAME = "style1"; //$NON-NLS-1$
        private static final String STYLE2_NAME = "style2"; //$NON-NLS-1$

        /* The map of basic styles */
        private static final Map<String, OutputElementStyle> STATE_MAP;
        /*
         * A map of styles names to a style that has the basic style as parent, to
         * avoid returning complete styles for each state
         */
        private static final Map<String, OutputElementStyle> STYLE_MAP;

        static {
            /* Build three different styles to use as examples */
            ImmutableMap.Builder<String, OutputElementStyle> builder = new ImmutableMap.Builder<>();

            builder.put(STYLE0_NAME, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, STYLE0_NAME,
                    StyleProperties.BACKGROUND_COLOR, String.valueOf(X11ColorUtils.toHexColor("blue")), //$NON-NLS-1$
                    StyleProperties.HEIGHT, 0.5f,
                    StyleProperties.OPACITY, 0.75f)));
            builder.put(STYLE1_NAME, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, STYLE1_NAME,
                    StyleProperties.BACKGROUND_COLOR, String.valueOf(X11ColorUtils.toHexColor("yellow")), //$NON-NLS-1$
                    StyleProperties.HEIGHT, 1.0f,
                    StyleProperties.OPACITY, 1.0f)));
            builder.put(STYLE2_NAME, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, STYLE2_NAME,
                    StyleProperties.BACKGROUND_COLOR, String.valueOf(X11ColorUtils.toHexColor("green")), //$NON-NLS-1$
                    StyleProperties.HEIGHT, 0.75f,
                    StyleProperties.OPACITY, 0.5f)));
            STATE_MAP = builder.build();

            /* build the style map too */
            builder = new ImmutableMap.Builder<>();
            builder.put(STYLE0_NAME, new OutputElementStyle(STYLE0_NAME));
            builder.put(STYLE1_NAME, new OutputElementStyle(STYLE1_NAME));
            builder.put(STYLE2_NAME, new OutputElementStyle(STYLE2_NAME));
            STYLE_MAP = builder.build();
        }

        private final BiMap<Long, Integer> fIDToDisplayQuark = HashBiMap.create();
        private ExampleStateSystemAnalysisModule fModule;

        /**
         * Constructor
         *
         * @param trace
         *            The trace this analysis is for
         * @param module
         *            The scripted analysis for this data provider
         */
        public ExampleTimeGraphDataProvider(ITmfTrace trace, ExampleStateSystemAnalysisModule module) {
            super(trace);
            fModule = module;
        }

        /**
         * Create the time graph data provider
         *
         * @param trace
         *            The trace for which is the data provider
         * @return The data provider
         */
        public static @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> create(ITmfTrace trace) {
            ExampleStateSystemAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, ExampleStateSystemAnalysisModule.class, ExampleStateSystemAnalysisModule.ID);
            return module != null ? new ExampleTimeGraphDataProvider(trace, module) : null;
        }

        @Override
        public TmfModelResponse<TmfTreeModel<@NonNull ITimeGraphEntryModel>> fetchTree(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
            fModule.waitForInitialization();
            ITmfStateSystem ss = fModule.getStateSystem();
            if (ss == null) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.ANALYSIS_INITIALIZATION_FAILED);
            }

            boolean isComplete = ss.waitUntilBuilt(0);
            long endTime = ss.getCurrentEndTime();

            // Make an entry for each base quark
            List<ITimeGraphEntryModel> entryList = new ArrayList<>();
            for (Integer quark : ss.getQuarks("CPUs", "*")) { //$NON-NLS-1$ //$NON-NLS-2$
                Long id = fIDToDisplayQuark.inverse().computeIfAbsent(quark, q -> sfAtomicId.getAndIncrement());
                entryList.add(new TimeGraphEntryModel(id, -1, ss.getAttributeName(quark), ss.getStartTime(), endTime));
            }

            Status status = isComplete ? Status.COMPLETED : Status.RUNNING;
            String msg = isComplete ? CommonStatusMessage.COMPLETED : CommonStatusMessage.RUNNING;
            return new TmfModelResponse<>(new TmfTreeModel<>(Collections.emptyList(), entryList), status, msg);
        }

        @Override
        public @NonNull String getId() {
            return ID;
        }

        @Override
        public @NonNull TmfModelResponse<TimeGraphModel> fetchRowModel(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
            ITmfStateSystem ss = fModule.getStateSystem();
            if (ss == null) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.ANALYSIS_INITIALIZATION_FAILED);
            }

            try {
                List<@NonNull ITimeGraphRowModel> rowModels = getDefaultRowModels(fetchParameters, ss, monitor);
                if (rowModels == null) {
                    rowModels = Collections.emptyList();
                }
                return new TmfModelResponse<>(new TimeGraphModel(rowModels), Status.COMPLETED, CommonStatusMessage.COMPLETED);
            } catch (IndexOutOfBoundsException | TimeRangeException | StateSystemDisposedException e) {
                return new TmfModelResponse<>(null, Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
            }
        }

        private @Nullable List<ITimeGraphRowModel> getDefaultRowModels(Map<String, Object> fetchParameters, ITmfStateSystem ss, @Nullable IProgressMonitor monitor) throws IndexOutOfBoundsException, TimeRangeException, StateSystemDisposedException {
            Map<Integer, ITimeGraphRowModel> quarkToRow = new HashMap<>();
            // Prepare the quarks to display
            Collection<Long> selectedItems = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
            if (selectedItems == null) {
                // No selected items, take them all
                selectedItems = fIDToDisplayQuark.keySet();
            }
            for (Long id : selectedItems) {
                Integer quark = fIDToDisplayQuark.get(id);
                if (quark != null) {
                    quarkToRow.put(quark, new TimeGraphRowModel(id, new ArrayList<>()));
                }
            }

            // This regex map automatically filters or highlights the entry
            // according to the global filter entered by the user
            Map<@NonNull Integer, @NonNull Predicate<@NonNull Multimap<@NonNull String, @NonNull Object>>> predicates = new HashMap<>();
            Multimap<@NonNull Integer, @NonNull String> regexesMap = DataProviderParameterUtils.extractRegexFilter(fetchParameters);
            if (regexesMap != null) {
                predicates.putAll(computeRegexPredicate(regexesMap));
            }

            // Query the state system to fill the states
            long currentEndTime = ss.getCurrentEndTime();
            for (ITmfStateInterval interval : ss.query2D(quarkToRow.keySet(), getTimes(ss, DataProviderParameterUtils.extractTimeRequested(fetchParameters)))) {
                if (monitor != null && monitor.isCanceled()) {
                    return Collections.emptyList();
                }
                ITimeGraphRowModel row = quarkToRow.get(interval.getAttribute());
                if (row != null) {
                    List<@NonNull ITimeGraphState> states = row.getStates();
                    ITimeGraphState timeGraphState = getStateFromInterval(interval, currentEndTime);
                    // This call will compare the state with the filter predicate
                    applyFilterAndAddState(states, timeGraphState, row.getEntryID(), predicates, monitor);
                }
            }
            for (ITimeGraphRowModel model : quarkToRow.values()) {
                model.getStates().sort(Comparator.comparingLong(ITimeGraphState::getStartTime));
            }

            return new ArrayList<>(quarkToRow.values());
        }

        private static TimeGraphState getStateFromInterval(ITmfStateInterval statusInterval, long currentEndTime) {
            long time = statusInterval.getStartTime();
            long duration = Math.min(currentEndTime, statusInterval.getEndTime() + 1) - time;
            Object o = statusInterval.getValue();
            if (!(o instanceof Long)) {
                // Add a null state
                return new TimeGraphState(time, duration, Integer.MIN_VALUE);
            }
            String styleName = "style" + ((Long) o) % 3; //$NON-NLS-1$
            return new TimeGraphState(time, duration, String.valueOf(o), STYLE_MAP.get(styleName));
        }

        private static Set<Long> getTimes(ITmfStateSystem key, @Nullable List<Long> list) {
            if (list == null) {
                return Collections.emptySet();
            }
            Set<@NonNull Long> times = new HashSet<>();
            for (long t : list) {
                if (key.getStartTime() <= t && t <= key.getCurrentEndTime()) {
                    times.add(t);
                }
            }
            return times;
        }

        @Override
        public @NonNull TmfModelResponse<List<ITimeGraphArrow>> fetchArrows(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
            /**
             * If there were arrows to be drawn, this is where they would be defined
             */
            return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        @Override
        public @NonNull TmfModelResponse<Map<String, String>> fetchTooltip(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
            /**
             * If there were tooltips to be drawn, this is where they would be
             * defined
             */
            return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        @Override
        public TmfModelResponse<OutputStyleModel> fetchStyle(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
            return new TmfModelResponse<>(new OutputStyleModel(STATE_MAP), Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

    }
```

## Data provider management

Data providers can be handled by the **DataProviderManager** class,
which uses an extension point and factories for data providers.

This manager associates a unique data provider per trace and extension
point ID, ensuring that data providers can be reused and that each entry
for a trace reuses the same unique entry ID.

### Data Provider Factories

The data provider manager requires a factory for the various data
providers, to create the data provider instances for a trace. Here is an
example of factory class to create the time graph data provider of the
previous section.

```java
    import java.util.Collection;
    import java.util.Collections;

    import org.eclipse.jdt.annotation.NonNull;
    import org.eclipse.jdt.annotation.Nullable;
    import org.eclipse.tracecompass.examples.core.analysis.ExampleStateSystemAnalysisModule;
    import org.eclipse.tracecompass.internal.tmf.core.model.DataProviderDescriptor;
    import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;
    import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor.ProviderType;
    import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
    import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
    import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
    import org.eclipse.tracecompass.tmf.core.model.xy.TmfTreeXYCompositeDataProvider;
    import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
    import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
    import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

    /**
     * An example of a time graph data provider factory.
     *
     * This factory is also in the developer documentation of Trace Compass. If it is
     * modified here, the doc should also be updated.
     *
     * @author Genevi�ve Bastien
     */
    @SuppressWarnings("restriction")
    public class ExampleTimeGraphProviderFactory implements IDataProviderFactory {

        private static final IDataProviderDescriptor DESCRIPTOR = new DataProviderDescriptor.Builder()
                .setId(ExampleTimeGraphDataProvider.ID)
                .setName("Example time graph data provider") //$NON-NLS-1$
                .setDescription("This is an example of a time graph data provider using a state system analysis as a source of data") //$NON-NLS-1$
                .setProviderType(ProviderType.TIME_GRAPH)
                .build();

        @Override
        public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(ITmfTrace trace) {
            Collection<@NonNull ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
            if (traces.size() == 1) {
                return ExampleTimeGraphDataProvider.create(trace);
            }
            return TmfTreeXYCompositeDataProvider.create(traces, "Example time graph data provider", ExampleTimeGraphDataProvider.ID); //$NON-NLS-1$
        }

        @Override
        public Collection<IDataProviderDescriptor> getDescriptors(@NonNull ITmfTrace trace) {
            ExampleStateSystemAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, ExampleStateSystemAnalysisModule.class, ExampleStateSystemAnalysisModule.ID);
            return module != null ? Collections.singletonList(DESCRIPTOR) : Collections.emptyList();
        }

    }
```

### Extension point

This extension needs to be added to the plugin's plugin.xml file:

```xml
    <extension point="org.eclipse.tracecompass.tmf.core.dataprovider">
        <dataProviderFactory
             class="org.eclipse.tracecompass.examples.core.data.provider.ExampleTimeGraphProviderFactory"
             id="org.eclipse.tracecompass.examples.timegraph.dataprovider">
        </dataProviderFactory>
        <dataProviderFactory
             class="org.eclipse.tracecompass.examples.core.data.provider.ExampleXYDataProviderFactory"
             id="org.eclipse.tracecompass.examples.xy.dataprovider">
        </dataProviderFactory>
    </extension>
```

### Experiments

In the data provider manager, experiments also get a unique instance of
a data provider, which can be specific or encapsulate the data providers
from the child traces. For example, an experiment can have its own
concrete data provider when required (an analysis that runs only on
experiments), or the factory would create a **CompositeDataProvider**
(using **TmfTreeXYCompositeDataProvider** or
**TmfTimeGraphCompositeDataProvider**) encapsulating the providers from
its traces. The benefit of encapsulating the providers from child traces
is that their entries/IDs can be reused, limiting the number of created
objects and ensuring consistency in views. These composite data
providers dispatch the request to all the encapsulated providers and
aggregates the results into the expected data structure.

## Utilities

Abstract base classes are provided for TreeXY and time graph data
providers based on **TmfStateSystemAnalysisModule**s
(**AbstractTreeCommonXDataProvider** and
**AbstractTimeGraphDataProvider**, respectively). They handle
concurrency, mapping of state system attributes to unique IDs,
exceptions, caching and encapsulating the model in a response with the
correct status.

## Views

Data providers are used to populate views. When the data provider is
well implemented and if the view does not require any additional
behavior, it should be straightforward to implement.

### XY views

XY data providers can be visualized with a view extending
**TmfChartView**. Here's an example of the minimal implementation
required to display its data.

The tree viewer (left part) can be an extension of the
**AbstractSelectTreeViewer** class, while the chart part, showing the XY
chart itself can extend **AbstractSelectTreeViewer**.

Out of the box, it supports experiments, updating during the trace
analysis, Pin & Clone and a number of chart viewer features.

```java
    import java.util.Comparator;
    import java.util.Objects;

    import org.eclipse.jdt.annotation.NonNull;
    import org.eclipse.jdt.annotation.Nullable;
    import org.eclipse.swt.widgets.Composite;
    import org.eclipse.tracecompass.examples.core.data.provider.ExampleXYDataProvider;
    import org.eclipse.tracecompass.tmf.ui.viewers.TmfViewer;
    import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractSelectTreeViewer;
    import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
    import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
    import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;
    import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.TmfXYChartViewer;
    import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfFilteredXYChartViewer;
    import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfXYChartSettings;
    import org.eclipse.tracecompass.tmf.ui.views.TmfChartView;

    import com.google.common.collect.ImmutableList;

    /**
     * An example of a data provider XY view
     *
     * @author Genevi�ve Bastien
     */
    public class ExampleXYDataProviderView extends TmfChartView {

        /** View ID. */
        public static final String ID = "org.eclipse.tracecompass.examples.dataprovider.xyview"; //$NON-NLS-1$

        /**
         * Constructor
         */
        public ExampleXYDataProviderView() {
            super("Example Tree XY View"); //$NON-NLS-1$
        }

        @Override
        protected TmfXYChartViewer createChartViewer(@Nullable Composite parent) {
            TmfXYChartSettings settings = new TmfXYChartSettings(null, null, null, 1);
            return new TmfFilteredXYChartViewer(parent, settings, ExampleXYDataProvider.ID);
        }

        private static final class TreeXyViewer extends AbstractSelectTreeViewer {

            public TreeXyViewer(Composite parent) {
                super(parent, 1, ExampleXYDataProvider.ID);
            }

            @Override
            protected ITmfTreeColumnDataProvider getColumnDataProvider() {
                return () -> ImmutableList.of(createColumn("Name", Comparator.comparing(TmfTreeViewerEntry::getName)), //$NON-NLS-1$
                        new TmfTreeColumnData("Legend")); //$NON-NLS-1$
            }
        }

        @Override
        protected @NonNull TmfViewer createLeftChildViewer(@Nullable Composite parent) {
            return new TreeXyViewer(Objects.requireNonNull(parent));
        }
    }
```

### Time Graph Views

For time graph view populated by a data provider, the base class to
extend would be the **BaseDataProviderTimeGraphView**. The default
implementation is fairly straightforward and if the the data provider
also provides styling through the **IOutputStyleProvider**, an instance
of the **BaseDataProviderTimeGraphPresentationProvider** can be used
directly.

If there is no styling though, all states would be drawn as black
states. In that case, a presentation provider will need to be added to
the view. It can extend **TimeGraphPresentationProvider**.

Here is the simplest implementation of the time graph view using a data
provider:

```java
    import org.eclipse.tracecompass.examples.core.data.provider.ExampleTimeGraphDataProvider;
    import org.eclipse.tracecompass.internal.provisional.tmf.ui.widgets.timegraph.BaseDataProviderTimeGraphPresentationProvider;
    import org.eclipse.tracecompass.tmf.ui.views.timegraph.BaseDataProviderTimeGraphView;

    /**
     * An example of a data provider time graph view
     *
     * @author Genevi�ve Bastien
     */
    @SuppressWarnings("restriction")
    public class ExampleTimeGraphDataProviderView extends BaseDataProviderTimeGraphView {

        /** View ID. */
        public static final String ID = "org.eclipse.tracecompass.examples.dataprovider.tgview"; //$NON-NLS-1$

        /**
         * Default constructor
         */
        public ExampleTimeGraphDataProviderView() {
           super(ID, new BaseDataProviderTimeGraphPresentationProvider(), ExampleTimeGraphDataProvider.ID);
        }

    }
```
