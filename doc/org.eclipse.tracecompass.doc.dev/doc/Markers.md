<!--TOC-->

- [Markers](#markers)
  - [Trace-specific markers](#trace-specific-markers)
  - [View-specific markers](#view-specific-markers)

<!--TOC-->

# Markers

Markers are annotations that are defined with a time range, a color, a
category and an optional label. The markers are displayed in the time
graph of any view that extends *AbstractTimeGraphView*. The markers are
drawn as a line or a region (in case the time range duration is not
zero) of the given color, which can have an alpha value to use
transparency. The markers can be drawn in the foreground (above time
graph states) or in the background (below time graph states). An
optional label can be drawn in the the time scale area.

The developer can add trace-specific markers and/or view-specific
markers.

## Trace-specific markers

Trace-specific markers can be added by registering an *IAdapterFactory*
with the TmfTraceAdapterManager. The adapter factory must provide
adapters of the *IMarkerEventSource* class for a given *ITmfTrace*
object. The adapter factory can be registered for traces of a certain
class (which will include sub-classes of the given class) or it can be
registered for traces of a certain trace type id (as defined in the
*org.eclipse.linuxtools.tmf.core.tracetype* extension point).

The adapter factory can be registered in the *Activator* of the plug-in
that introduces it, in the *start()* method, and unregistered in the
*stop()* method.

It is recommended to extend the *AbstractTmfTraceAdapterFactory* class
when creating the adapter factory. This will ensure that a single
instance of the adapter is created for a specific trace and reused by
all components that need the adapter, and that the adapter is disposed
when the trace is closed.

The adapter implementing the *IMarkerEventSource* interface must provide
two methods:

- *getMarkerCategories()* returns a list of category names which will be
  displayed to the user, who can then enable or disable markers on a
  per-category basis.

<!-- -->

- *getMarkerList()* returns a list of markers instances of class
  *IMarkerEvent* for the given category and time range. The resolution
  can be used to limit the number of markers returned for the current
  zoom level, and the progress monitor can be checked for early
  cancellation of the marker computation.

The trace-specific markers for a particular trace will appear in all
views extending *AbstractTimeGraphView* when that trace (or an
experiment containing that trace) is selected.

An example of a trace-specific markers implementation can be seen by
examining classes *LostEventsMarkerEventSourceFactory*,
*LostEventsMarkerEventSource* and *Activator* in the
*org.eclipse.tracecompass.tmf.ui* plug-in.

## View-specific markers

View-specific markers can by added in sub-classes of
*AbstractTimeGraphView* by implementing the following two methods:

- *getViewMarkerCategories()* returns a list of category names which
  will be displayed to the user, who can then enable or disable markers
  on a per-category basis.

<!-- -->

- *getViewMarkerList()* returns a list of markers instances of class
  *IMarkerEvent* for the given time range. The resolution can be used to
  limit the number of markers returned for the current zoom level, and
  the progress monitor can be checked for early cancellation of the
  marker computation.
