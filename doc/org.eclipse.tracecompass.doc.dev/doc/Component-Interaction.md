# Component Interaction

TMF provides a mechanism for different components to interact with each
other using signals. The signals can carry information that is specific
to each signal.

The TMF Signal Manager handles registration of components and the
broadcasting of signals to their intended receivers.

Components can register as VIP receivers which will ensure they will
receive the signal before non-VIP receivers.

## Sending Signals

In order to send a signal, an instance of the signal must be created and
passed as argument to the signal manager to be dispatched. Every
component that can handle the signal will receive it. The receivers do
not need to be known by the sender.

```java
    TmfExampleSignal signal = new TmfExampleSignal(this, ...);
    TmfSignalManager.dispatchSignal(signal);
```

If the sender is an instance of the class TmfComponent, the broadcast
method can be used:

```java
    TmfExampleSignal signal = new TmfExampleSignal(this, /*...*/);
    broadcast(signal);
```

## Receiving Signals

In order to receive any signal, the receiver must first be registered
with the signal manager. The receiver can register as a normal or VIP
receiver.

```java
    TmfSignalManager.register(this);
    TmfSignalManager.registerVIP(this);
```

If the receiver is an instance of the class TmfComponent, it is
automatically registered as a normal receiver in the constructor.

When the receiver is destroyed or disposed, it should deregister itself
from the signal manager.

```java
    TmfSignalManager.deregister(this);
```

To actually receive and handle any specific signal, the receiver must
use the @TmfSignalHandler annotation and implement a method that will be
called when the signal is broadcast. The name of the method is
irrelevant.

```java
    @TmfSignalHandler
    public void example(TmfExampleSignal signal) {
        ...
    }
```

The source of the signal can be used, if necessary, by a component to
filter out and ignore a signal that was broadcast by itself when the
component is also a receiver of the signal but only needs to handle it
when it was sent by another component or another instance of the
component.

## Signal Throttling

It is possible for a TmfComponent instance to buffer the dispatching of
signals so that only the last signal queued after a specified delay
without any other signal queued is sent to the receivers. All signals
that are preempted by a newer signal within the delay are discarded.

The signal throttler must first be initialized:

```java
    final int delay = 100; // in ms
    TmfSignalThrottler throttler = new TmfSignalThrottler(this, delay);
```

Then the sending of signals should be queued through the throttler:

```java
    TmfExampleSignal signal = new TmfExampleSignal(this, ...);
    throttler.queue(signal);
```

When the throttler is no longer needed, it should be disposed:

```java
    throttler.dispose();
```

## Ignoring inbound/outbound signals

It is possible to stop certain signals from being sent or received.

To block all incoming signals to an object:

```java
    TmfSignalManager.addIgnoredInboundSignal(objectInstance, TmfSignal.class);
```

To block all outgoing signals originating from an object:

```java
    TmfSignalManager.addIgnoredOutboundSignal(objectInstance, TmfSignal.class);
```

The blocked signal filtering is based on type hierarchy. Blocking
`TmfSignal.class` will result in blocking all signals derived from
TmfSignal. Blocking `TmfTraceSelectedSignal` will block all signals of
this type and derived signals from `TmfTraceSelectedSignal`

To remove an ignore rule or clear them all:

```java
    TmfSignalManager.removeIgnoredOutboundSignal(Object source, Class<? extends TmfSignal> signal)
    TmfSignalManager.removeIgnoredInboundSignal(Object listener, Class<? extends TmfSignal> signal)
    TmfSignalManager.clearIgnoredOutboundSignalList(Object source)
    TmfSignalManager.clearIgnoredInboundSignalList(Object listener)
```

## Signal Reference

The following is a list of built-in signals defined in the framework.

### TmfStartSynchSignal

*Purpose*

This signal is used to indicate the start of broadcasting of a signal.
Internally, the data provider will not fire event requests until the
corresponding TmfEndSynchSignal signal is received. This allows
coalescing of requests triggered by multiple receivers of the broadcast
signal.

*Senders*

Sent by TmfSignalManager before dispatching a signal to all receivers.

*Receivers*

Received by TmfDataProvider.

### TmfEndSynchSignal

*Purpose*

This signal is used to indicate the end of broadcasting of a signal.
Internally, the data provider fire all pending event requests that were
received and buffered since the corresponding TmfStartSynchSignal signal
was received. This allows coalescing of requests triggered by multiple
receivers of the broadcast signal.

*Senders*

Sent by TmfSignalManager after dispatching a signal to all receivers.

*Receivers*

Received by TmfDataProvider.

### TmfTraceOpenedSignal

*Purpose*

This signal is used to indicate that a trace has been opened in an
editor.

*Senders*

Sent by a TmfEventsEditor instance when it is created.

*Receivers*

Received by TmfTrace, TmfExperiment, TmfTraceManager and every view that
shows trace data. Components that show trace data should handle this
signal.

### TmfTraceSelectedSignal

*Purpose*

This signal is used to indicate that a trace has become the currently
selected trace.

*Senders*

Sent by a TmfEventsEditor instance when it receives focus. Components
can send this signal to make a trace editor be brought to front.

*Receivers*

Received by TmfTraceManager and every view that shows trace data.
Components that show trace data should handle this signal.

### TmfTraceClosedSignal

*Purpose*

This signal is used to indicate that a trace editor has been closed.

*Senders*

Sent by a TmfEventsEditor instance when it is disposed.

*Receivers*

Received by TmfTraceManager and every view that shows trace data.
Components that show trace data should handle this signal.

### TmfTraceRangeUpdatedSignal

*Purpose*

This signal is used to indicate that the valid time range of a trace has
been updated. This triggers indexing of the trace up to the end of the
range. In the context of streaming, this end time is considered a safe
time up to which all events are guaranteed to have been completely
received. For non-streaming traces, the end time is set to infinity
indicating that all events can be read immediately. Any processing of
trace events that wants to take advantage of request coalescing should
be triggered by this signal.

*Senders*

Sent by TmfExperiment and non-streaming TmfTrace. Streaming traces
should send this signal in the TmfTrace subclass when a new safe time is
determined by a specific implementation.

*Receivers*

Received by TmfTrace, TmfExperiment and components that process trace
events. Components that need to process trace events should handle this
signal.

### TmfTraceUpdatedSignal

*Purpose*

This signal is used to indicate that new events have been indexed for a
trace.

*Senders*

Sent by TmfCheckpointIndexer when new events have been indexed and the
number of events has changed.

*Receivers*

Received by components that need to be notified of a new trace event
count.

### TmfSelectionRangeUpdatedSignal

*Purpose*

This signal is used to indicate that a new time or time range has been
selected. It contains a begin and end time. If a single time is selected
then the begin and end time are the same.

*Senders*

Sent by any component that allows the user to select a time or time
range.

*Receivers*

Received by any component that needs to be notified of the currently
selected time or time range.

### TmfWindowRangeUpdatedSignal

*Purpose*

This signal is used to indicate that a new time range window has been
set.

*Senders*

Sent by any component that allows the user to set a time range window.

*Receivers*

Received by any component that needs to be notified of the current
visible time range window.

### TmfEventFilterAppliedSignal

*Purpose*

This signal is used to indicate that a filter has been applied to a
trace.

*Senders*

Sent by TmfEventsTable when a filter is applied.

*Receivers*

Received by any component that shows trace data and needs to be notified
of applied filters.

### TmfEventSearchAppliedSignal

*Purpose*

This signal is used to indicate that a search has been applied to a
trace.

*Senders*

Sent by TmfEventsTable when a search is applied.

*Receivers*

Received by any component that shows trace data and needs to be notified
of applied searches.

### TmfTimestampFormatUpdateSignal

*Purpose*

This signal is used to indicate that the timestamp format preference has
been updated.

*Senders*

Sent by TmfTimestampFormat when the default timestamp format preference
is changed.

*Receivers*

Received by any component that needs to refresh its display for the new
timestamp format.

### TmfStatsUpdatedSignal

*Purpose*

This signal is used to indicate that the statistics data model has been
updated.

*Senders*

Sent by statistic providers when new statistics data has been processed.

*Receivers*

Received by statistics viewers and any component that needs to be
notified of a statistics update.

### TmfPacketStreamSelected

*Purpose*

This signal is used to indicate that the user has selected a packet
stream to analyze.

*Senders*

Sent by the Stream List View when the user selects a new packet stream.

*Receivers*

Received by views that analyze packet streams.

### TmfStartAnalysisSignal

*Purpose*

This signal is used to indicate that an analysis has started.

*Senders*

Sent by an analysis module when it starts to execute the analyis.

*Receivers*

Received by components that need to be notified of the start of an
analysis or that need to receive the analysis module.

### TmfCpuSelectedSignal

*Purpose*

This signal is used to indicate that the user has selected a CPU core.

*Senders*

Sent by any component that allows the user to select a CPU.

*Receivers*

Received by viewers that show information specific to a selected CPU.

### TmfThreadSelectedSignal

*Purpose*

This signal is used to indicate that the user has selected a thread.

*Senders*

Sent by any component that allows the user to select a thread.

*Receivers*

Received by viewers that show information specific to a selected thread.

### TmfSymbolProviderUpdatedSignal

*Purpose*

This signal is used to indicate that the user has updated the symbol
mapping.

*Senders*

Sent by symbol providers or managers when more information is available.

*Receivers*

Received by viewers that show information specific to mapped symbol,
typically a function call.

### TmfTraceSynchronizedSignal

*Purpose*

This signal is used to indicate that trace synchronization has been
completed.

*Senders*

Sent by the experiment after trace synchronization.

*Receivers*

Received by any component that needs to be notified of trace
synchronization.

### TmfMarkerEventSourceUpdatedSignal

*Purpose*

This signal is used to indicate that a marker event source has been
updated.

*Senders*

Sent by a component that has triggered a change in a marker event
source.

*Receivers*

Received by any component that needs to refresh the markers due to the
change in marker event source.

## Debugging

TMF has built-in Eclipse tracing support for the debugging of signal
interaction between components. To enable it, open the **Run/Debug
Configuration...** dialog, select a configuration, click the **Tracing**
tab, select the plug-in **org.eclipse.tracecompass.tmf.core**, and check
the **signal** item.

All signals sent and received will be logged to the file TmfTrace.log
located in the Eclipse home directory.
