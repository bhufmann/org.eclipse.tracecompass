# Generic State System

## Introduction

The Generic State System is a utility available in TMF to track
different states over the duration of a trace. It works by first sending
some or all events of the trace into a state provider, which defines the
state changes for a given trace type. Once built, views and analysis
modules can then query the resulting database of states (called "state
history") to get information.

For example, let's suppose we have the following sequence of events in a
kernel trace:

`10 s, sys_open, fd = 5, file = /home/user/myfile`  
`...`  
`15 s, sys_read, fd = 5, size=32`  
`...`  
`20 s, sys_close, fd = 5`

Now let's say we want to implement an analysis module which will track
the amount of bytes read and written to each file. Here, of course the
sys_read is interesting. However, by just looking at that event, we have
no information on which file is being read, only its fd (5) is known. To
get the match fd5 = /home/user/myfile, we have to go back to the
sys_open event which happens 5 seconds earlier.

But since we don't know exactly where this sys_open event is, we will
have to go back to the very start of the trace, and look through events
one by one! This is obviously not efficient, and will not scale well if
we want to analyze many similar patterns, or for very large traces.

A solution in this case would be to use the state system to keep track
of the amount of bytes read/written to every \*filename\* (instead of
every file descriptor, like we get from the events). Then the module
could ask the state system "what is the amount of bytes read for file
"/home/user/myfile" at time 16 s", and it would return the answer "32"
(assuming there is no other read than the one shown).

## High-level components

The State System infrastructure is composed of 3 parts:

- The state provider
- The central state system
- The storage backend

The state provider is the customizable part. This is where the mapping
from trace events to state changes is done. This is what you want to
implement for your specific trace type and analysis type. It's
represented by the ITmfStateProvider interface (with a threaded
implementation in AbstractTmfStateProvider, which you can extend).

The core of the state system is exposed through the ITmfStateSystem and
ITmfStateSystemBuilder interfaces. The former allows only read-only
access and is typically used for views doing queries. The latter also
allows writing to the state history, and is typically used by the state
provider.

Finally, each state system has its own separate backend. This determines
how the intervals, or the "state history", are saved (in RAM, on disk,
etc.) You can select the type of backend at construction time in the
TmfStateSystemFactory.

## Definitions

Before we dig into how to use the state system, we should go over some
useful definitions:

### Attribute

An attribute is the smallest element of the model that can be in any
particular state. When we refer to the "full state", in fact it means we
are interested in the state of every single attribute of the model.

### Attribute Tree

Attributes in the model can be placed in a tree-like structure, a bit
like files and directories in a file system. However, note that an
attribute can always have both a value and sub-attributes, so they are
like files and directories at the same time. We are then able to refer
to every single attribute with its path in the tree.

For example, in the attribute tree for Linux kernel traces, we use the
following attributes, among others:

    |- Processes
    |    |- 1000
    |    |   |- PPID
    |    |   |- Exec_name
    |    |- 1001
    |    |   |- PPID
    |    |   |- Exec_name
    |   ...
    |- CPUs
         |- 0
         |  |- Status
         |  |- Current_pid
        ...

In this model, the attribute "Processes/1000/PPID" refers to the PPID of
process with PID 1000. The attribute "CPUs/0/Status" represents the
status (running, idle, etc.) of CPU 0. "Processes/1000/PPID" and
"Processes/1001/PPID" are two different attribute, even though their
base name is the same: the whole path is the unique identifier.

The value of each attribute can change over the duration of the trace,
independently of the other ones, and independently of its position in
the tree.

The tree-like organization is optional, all attributes could be at the
same level. But it's possible to put them in a tree, and it helps make
things clearer.

### Quark

In addition to a given path, each attribute also has a unique integer
identifier, called the "quark". To continue with the file system
analogy, this is like the inode number. When a new attribute is created,
a new unique quark will be assigned automatically. They are assigned
incrementally, so they will normally be equal to their order of
creation, starting at 0.

Methods are offered to get the quark of an attribute from its path. The
API methods for inserting state changes and doing queries normally use
quarks instead of paths. This is to encourage users to cache the quarks
and re-use them, which avoids re-walking the attribute tree over and
over, which avoids unneeded hashing of strings.

### State value

The path and quark of an attribute will remain constant for the whole
duration of the trace. However, the value carried by the attribute will
change. The value of a specific attribute at a specific time is called
the state value.

In the TMF implementation, state values can be integers, longs, doubles,
or strings. There is also a "null value" type, which is used to indicate
that no particular value is active for this attribute at this time, but
without resorting to a 'null' reference.

Any other type of value could be used, as long as the backend knows how
to store it.

Note that the TMF implementation also forces every attribute to always
carry the same type of state value. This is to make it simpler for
views, so they can expect that an attribute will always use a given
type, without having to check every single time. Null values are an
exception, they are always allowed for all attributes, since they can
safely be "unboxed" into all types.

### State change

A state change is the element that is inserted in the state system. It
consists of:

- a timestamp (the time at which the state change occurs)
- an attribute (the attribute whose value will change)
- a state value (the new value that the attribute will carry)

It's not an object per se in the TMF implementation (it's represented by
a function call in the state provider). Typically, the state provider
will insert zero, one or more state changes for every trace event,
depending on its event type, payload, etc.

Note, we use "timestamp" here, but it's in fact a generic term that
could be referred to as "index". For example, if a given trace type has
no notion of timestamp, the event rank could be used.

In the TMF implementation, the timestamp is a long (64-bit integer).

### State interval

State changes are inserted into the state system, but state intervals
are the objects that come out on the other side. Those are stocked in
the storage backend. A state interval represents a "state" of an
attribute we want to track. When doing queries on the state system,
intervals are what is returned. The components of a state interval are:

- Start time
- End time
- State value
- Quark

The start and end times represent the time range of the state. The state
value is the same as the state value in the state change that started
this interval. The interval also keeps a reference to its quark,
although you normally know your quark in advance when you do queries.

### State history

The state history is the name of the container for all the intervals
created by the state system. The exact implementation (how the intervals
are stored) is determined by the storage backend that is used.

Some backends will use a state history that is persistent on disk,
others do not. When loading a trace, if a history file is available and
the backend supports it, it will be loaded right away, skipping the need
to go through another construction phase.

### Construction phase

Before we can query a state system, we need to build the state history
first. To do so, trace events are sent one-by-one through the state
provider, which in turn sends state changes to the central component,
which then creates intervals and stores them in the backend. This is
called the construction phase.

Note that the state system needs to receive its events into
chronological order. This phase will end once the end of the trace is
reached.

Also note that it is possible to query the state system while it is
being build. Any timestamp between the start of the trace and the
current end time of the state system (available with
ITmfStateSystem#getCurrentEndTime()) is a valid timestamp that can be
queried.

### Queries

As mentioned previously, when doing queries on the state system, the
returned objects will be state intervals. In most cases it's the state
\*value\* we are interested in, but since the backend has to instantiate
the interval object anyway, there is no additional cost to return the
interval instead. This way we also get the start and end times of the
state "for free".

There are two types of queries that can be done on the state system:

#### Full queries

A full query means that we want to retrieve the whole state of the model
for one given timestamp. As we remember, this means "the state of every
single attribute in the model". As parameter we only need to pass the
timestamp (see the API methods below). The return value will be an array
of intervals, where the offset in the array represents the quark of each
attribute.

#### Single queries

In other cases, we might only be interested in the state of one
particular attribute at one given timestamp. For these cases it's better
to use a single query. For a single query, we need to pass both a
timestamp and a quark in parameter. The return value will be a single
interval, representing the state that this particular attribute was at
that time.

Single queries are typically faster than full queries (but once again,
this depends on the backend that is used), but not by much. Even if you
only want the state of say 10 attributes out of 200, it could be faster
to use a full query and only read the ones you need. Single queries
should be used for cases where you only want one attribute per timestamp
(for example, if you follow the state of the same attribute over a time
range).

#### 2D queries

2D queries are useful and more efficient than the previous two when
querying several attributes at multiple time stamps. This type of query
returns an iterable of the intervals matching the queried attributes and
that overlap the queried time range or one of the queried time stamps.
It is more efficient because it batches the queries and searches the
backend for all the desired intervals in a single pass. The returned
iterable is lazily evaluated, so it can be interrupted in an
intermediate state at no cost. This type of query is recommended for
querying backends to populate views where a subset of attributes are
queried on a sampled time range. The returned iterable is **not
ordered** to limit overhead.

## Relevant interfaces/classes

This section will describe the public interface and classes that can be
used if you want to use the state system.

### Main classes in org.eclipse.tracecompass.tmf.core.statesystem

#### ITmfStateProvider / AbstractTmfStateProvider

ITmfStateProvider is the interface you have to implement to define your
state provider. This is where most of the work has to be done to use a
state system for a custom trace type or analysis type.

For first-time users, it's recommended to extend
AbstractTmfStateProvider instead. This class takes care of all the
initialization mumbo-jumbo, and also runs the event handler in a
separate thread. You will only need to implement eventHandle, which is
the call-back that will be called for every event in the trace.

For an example, you can look at StatsStateProvider in the TMF tree, or
at the small example below.

#### TmfStateSystemFactory

Once you have defined your state provider, you need to tell your trace
type to build a state system with this provider during its
initialization. This consists of overriding TmfTrace#buildStateSystems()
and in there of calling the method in TmfStateSystemFactory that
corresponds to the storage backend you want to use (see the section
<a href="#Comparison_of_state_system_backends" class="wikilink"
title="#Comparison of state system backends">#Comparison of state system
backends</a>).

You will have to pass in parameter the state provider you want to use,
which you should have defined already. Each backend can also ask for
more configuration information.

You must then call registerStateSystem(id, statesystem) to make your
state system visible to the trace objects and the views. The ID can be
any string of your choosing. To access this particular state system, the
views or modules will need to use this ID.

Also, don't forget to call super.buildStateSystems() in your
implementation, unless you know for sure you want to skip the state
providers built by the super-classes.

You can look at how LttngKernelTrace does it for an example. It could
also be possible to build a state system only under certain conditions
(like only if the trace contains certain event types).

#### ITmfStateSystem

ITmfStateSystem is the main interface through which views or analysis
modules will access the state system. It offers a read-only view of the
state system, which means that no states can be inserted, and no
attributes can be created. Calling TmfTrace#getStateSystems().get(id)
will return you a ITmfStateSystem view of the requested state system.
The main methods of interest are:

##### getQuarkAbsolute()/getQuarkRelative()

Those are the basic quark-getting methods. The goal of the state system
is to return the state values of given attributes at given timestamps.
As we've seen earlier, attributes can be described with a
file-system-like path. The goal of these methods is to convert from the
path representation of the attribute to its quark.

Since quarks are created on-the-fly, there is no guarantee that the same
attributes will have the same quark for two traces of the same type. The
views should always query their quarks when dealing with a new trace or
a new state provider. Beyond that however, quarks should be cached and
reused as much as possible, to avoid potentially costly string
re-hashing.

getQuarkAbsolute() takes a variable amount of Strings in parameter,
which represent the full path to the attribute. Some of them can be
constants, some can come programmatically, often from the event's
fields.

getQuarkRelative() is to be used when you already know the quark of a
certain attribute, and want to access on of its sub-attributes. Its
first parameter is the origin quark, followed by a String varagrs which
represent the relative path to the final attribute.

These two methods will throw an AttributeNotFoundException if trying to
access an attribute that does not exist in the model.

These methods also imply that the view has the knowledge of how the
attribute tree is organized. This should be a reasonable hypothesis,
since the same analysis plugin will normally ship both the state
provider and the view, and they will have been written by the same
person. In other cases, it's possible to use getSubAttributes() to
explore the organization of the attribute tree first.

##### optQuarkAbsolute()/optQuarkRelative()

These two methods are similar to their counterparts getQuarkAbsolute()
and getQuarkRelative(). The only difference is that if the referenced
attribute does not exist, the value ITmfStateSystem#INVALID_ATTRIBUTE
(-2) is returned instead of throwing an exception.

These methods should be used when the presence of the referenced
attribute is known to be optional, to avoid the performance cost of
generating exceptions.

##### getQuarks()

This method (with or without a starting node quark) takes an attribute
path array which may contain wildcard "\*" or parent ".." elements, and
returns the list of matching attribute quarks. If no matching attribute
is found, an empty list is returned.

##### waitUntilBuilt()

This is a simple method used to block the caller until the construction
phase of this state system is done. If the view prefers to wait until
all information is available before starting to do queries (to get all
known attributes right away, for example), this is the guy to call.

##### queryFullState()

This is the method to do full queries. As mentioned earlier, you only
need to pass a target timestamp in parameter. It will return a List of
state intervals, in which the offset corresponds to the attribute quark.
This will represent the complete state of the model at the requested
time.

##### querySingleState()

The method to do single queries. You pass in parameter both a timestamp
and an attribute quark. This will return the single state matching this
timestamp/attribute pair.

Other methods are available, you are encouraged to read their Javadoc
and see if they can be potentially useful.

#### ITmfStateSystemBuilder

ITmfStateSystemBuilder is the read-write interface to the state system.
It extends ITmfStateSystem itself, so all its methods are available. It
then adds methods that can be used to write to the state system, either
by creating new attributes of inserting state changes.

It is normally reserved for the state provider and should not be visible
to external components. However it will be available in
AbstractTmfStateProvider, in the field 'ss'. That way you can call
ss.modifyAttribute() etc. in your state provider to write to the state.

The main methods of interest are:

##### getQuark\*AndAdd()

getQuarkAbsoluteAndAdd() and getQuarkRelativeAndAdd() work exactly like
their non-AndAdd counterparts in ITmfStateSystem. The difference is that
the -AndAdd versions will not throw any exception: if the requested
attribute path does not exist in the system, it will be created, and its
newly-assigned quark will be returned.

When in a state provider, the -AndAdd version should normally be used
(unless you know for sure the attribute already exist and don't want to
create it otherwise). This means that there is no need to define the
whole attribute tree in advance, the attributes will be created
on-demand.

##### modifyAttribute()

This is the main state-change-insertion method. As was explained before,
a state change is defined by a timestamp, an attribute and an object
representing the value of this state. Those three elements need to be
passed to modifyAttribute as parameters.

Other state change insertion methods are available (increment-, push-,
pop- and removeAttribute()), but those are simply convenience wrappers
around modifyAttribute(). Check their Javadoc for more information.

##### closeHistory()

When the construction phase is done, do not forget to call
closeHistory() to tell the backend that no more intervals will be
received. Depending on the backend type, it might have to save files,
close descriptors, etc. This ensures that a persistent file can then be
re-used when the trace is opened again.

If you use the AbstractTmfStateProvider, it will call closeHistory()
automatically when it reaches the end of the trace.

### Other relevant interfaces

#### ITmfStateValue

This is the interface used to represent state values. Those are used
when inserting state changes in the provider, and is also part of the
state intervals obtained when doing queries.

The abstract TmfStateValue class contains the factory methods to create
new state values of either int, long, double or string types. To
retrieve the real object inside the state value, one can use the
.unbox\* methods.

Note: Do not instantiate null values manually, use
TmfStateValue.nullValue()

#### ITmfStateInterval

This is the interface to represent the state intervals, which are stored
in the state history backend, and are returned when doing state system
queries. A very simple implementation is available in TmfStateInterval.
Its methods should be self-descriptive.

### Exceptions

The following exceptions, found in o.e.t.statesystem.core.exceptions,
are related to state system activities.

#### AttributeNotFoundException

This is thrown by getQuarkRelative() and getQuarkAbsolute() (but not by
the -AndAdd versions!) when passing an attribute path that is not
present in the state system. This is to ensure that no new attribute is
created when using these versions of the methods.

Views can expect some attributes to be present, but they should handle
these exceptions for when the attributes end up not being in the state
system (perhaps this particular trace didn't have a certain type of
events, etc.)

#### StateValueTypeException

This exception will be thrown when trying to unbox a state value into a
type different than its own. You should always check with
ITmfStateValue#getType() beforehand if you are not sure about the type
of a given state value.

#### TimeRangeException

This exception is thrown when trying to do a query on the state system
for a timestamp that is outside of its range. To be safe, you should
check with ITmfStateSystem#getStartTime() and #getCurrentEndTime() for
the current valid range of the state system. This is especially
important when doing queries on a state system that is currently being
built.

#### StateSystemDisposedException

This exception is thrown when trying to access a state system that has
been disposed, with its dispose() method. This can potentially happen at
shutdown, since Eclipse is not always consistent with the order in which
the components are closed.

## Comparison of state system backends

As we have seen in section
<a href="#High-level_components" class="wikilink"
title="#High-level components">#High-level components</a>, the state
system needs a storage backend to save the intervals. Different
implementations are available when building your state system from
TmfStateSystemFactory.

Do not confuse full/single queries with full/partial history! All
backend types should be able to handle any type of queries defined in
the ITmfStateSystem API, unless noted otherwise.

### Full history

Available with TmfStateSystemFactory#newFullHistory(). The full history
uses a History Tree data structure, which is an optimized structure
store state intervals on disk. Once built, it can respond to queries in
a *log(n)* manner.

You need to specify a file at creation time, which will be the container
for the history tree. Once it's completely built, it will remain on disk
(until you delete the trace from the project). This way it can be reused
from one session to another, which makes subsequent loading time much
faster.

This the backend used by the LTTng kernel plugin. It offers good
scalability and performance, even at extreme sizes (it's been tested
with traces of sizes up to 500 GB). Its main downside is the amount of
disk space required: since every single interval is written to disk, the
size of the history file can quite easily reach and even surpass the
size of the trace itself.

### Null history

Available with TmfStateSystemFactory#newNullHistory(). As its name
implies the null history is in fact an absence of state history. All its
query methods will return null (see the Javadoc in NullBackend).

Obviously, no file is required, and almost no memory space is used.

It's meant to be used in cases where you are not interested in past
states, but only in the "ongoing" one. It can also be useful for
debugging and benchmarking.

### In-memory history

Available with TmfStateSystemFactory#newInMemHistory(). This is a simple
wrapper using a TreeSet to store all state intervals in memory. The
implementation at the moment is quite simple, it will perform a binary
search on entries when doing queries to find the ones that match.

The advantage of this method is that it's very quick to build and query,
since all the information resides in memory. However, you are limited to
2^31 entries (roughly 2 billions), and depending on your state provider
and trace type, that can happen really fast!

There are no safeguards, so if you bust the limit you will end up with
ArrayOutOfBoundsException's everywhere. If your trace or state history
can be arbitrarily big, it's probably safer to use a Full History
instead.

### Partial history

Available with TmfStateSystemFactory#newPartialHistory(). The partial
history is a more advanced form of the full history. Instead of writing
all state intervals to disk like with the full history, we only write a
small fraction of them, and go back to read the trace to recreate the
states in-between.

It has a big advantage over a full history in terms of disk space usage.
It's very possible to reduce the history tree file size by a factor of
1000, while keeping query times within a factor of two. Its main
downside comes from the fact that you cannot do efficient single queries
with it (they are implemented by doing full queries underneath).

This makes it a poor choice for views like the Control Flow view, where
you do a lot of range queries and single queries. However, it is a
perfect fit for cases like statistics, where you usually do full queries
already, and you store lots of small states which are very easy to
"compress".

However, it can't really be used until bug 409630 is fixed.

## State System Operations

TmfStateSystemOperations is a static class that implements additional
statistical operations that can be performed on attributes of the state
system.

These operations require that the attribute be one of the numerical
values (int, long or double).

The speed of these operations can be greatly improved for large data
sets if the attribute was inserted in the state system as a mipmap
attribute. Refer to the
<a href="#Mipmap_feature" class="wikilink" title=" Mipmap feature">
Mipmap feature</a> section.

##### queryRangeMax()

This method returns the maximum numerical value of an attribute in the
specified time range. The attribute must be of type int, long or double.
Null values are ignored. The returned value will be of the same state
value type as the base attribute, or a null value if there is no state
interval stored in the given time range.

##### queryRangeMin()

This method returns the minimum numerical value of an attribute in the
specified time range. The attribute must be of type int, long or double.
Null values are ignored. The returned value will be of the same state
value type as the base attribute, or a null value if there is no state
interval stored in the given time range.

##### queryRangeAverage()

This method returns the average numerical value of an attribute in the
specified time range. The attribute must be of type int, long or double.
Each state interval value is weighted according to time. Null values are
counted as zero. The returned value will be a double primitive, which
will be zero if there is no state interval stored in the given time
range.

## Code example

Here is a small example of code that will use the state system. For this
example, let's assume we want to track the state of all the CPUs in a
LTTng kernel trace. To do so, we will watch for the "sched_switch" event
in the state provider, and will update an attribute indicating if the
associated CPU should be set to "running" or "idle".

We will use an attribute tree that looks like this:

    CPUs
     |--0
     |  |--Status
     |
     |--1
     |  |--Status
     |
     |  2
     |  |--Status
    ...

The second-level attributes will be named from the information available
in the trace events. Only the "Status" attributes will carry a state
value (this means we could have just used "1", "2", "3",... directly,
but we'll do it in a tree for the example's sake).

Also, we will use integer state values to represent "running" or "idle",
instead of saving the strings that would get repeated every time. This
will help in reducing the size of the history file.

First we will define a state provider in MyStateProvider. Then, we
define an analysis module that takes care of creating the state
provider. The analysis module will also contain code that can query the
state system.

### State Provider

    import java.util.Objects;

    import org.eclipse.jdt.annotation.NonNull;
    import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
    import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
    import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
    import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
    import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
    import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
    import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

    /**
     * An example of a simple state provider for a simple state system analysis
     *
     * @author Alexandre Montplaisir
     * @author Geneviève Bastien
     */
    public class ExampleStateProvider extends AbstractTmfStateProvider {

        private static final @NonNull String PROVIDER_ID = "org.eclipse.tracecompass.examples.state.provider"; //$NON-NLS-1$
        private static final int VERSION = 0;

        /**
         * Constructor
         *
         * @param trace
         *            The trace for this state provider
         */
        public ExampleStateProvider(@NonNull ITmfTrace trace) {
            super(trace, PROVIDER_ID);
        }

        @Override
        public int getVersion() {
            return VERSION;
        }

        @Override
        public @NonNull ITmfStateProvider getNewInstance() {
            return new ExampleStateProvider(getTrace());
        }

        @Override
        protected void eventHandle(ITmfEvent event) {

            /**
             * Do what needs to be done with this event, here is an example that
             * updates the CPU state and TID after a sched_switch
             */
            if (event.getName().equals("sched_switch")) { //$NON-NLS-1$

                final long ts = event.getTimestamp().getValue();
                Long nextTid = event.getContent().getFieldValue(Long.class, "next_tid"); //$NON-NLS-1$
                Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
                if (cpu == null || nextTid == null) {
                    return;
                }

                ITmfStateSystemBuilder ss = Objects.requireNonNull(getStateSystemBuilder());
                int quark = ss.getQuarkAbsoluteAndAdd("CPUs", String.valueOf(cpu)); //$NON-NLS-1$

                // The status attribute has an integer value
                int statusQuark = ss.getQuarkRelativeAndAdd(quark, "Status"); //$NON-NLS-1$
                Integer value = (nextTid > 0 ? 1 : 0);
                ss.modifyAttribute(ts, value, statusQuark);

                // The main quark contains the tid of the running thread
                ss.modifyAttribute(ts, nextTid, quark);
            }
        }

    }

### Analysis module definition

    import java.util.Objects;

    import org.eclipse.jdt.annotation.NonNull;
    import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
    import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;

    /**
     * An example of a simple state system analysis module.
     *
     * @author Geneviève Bastien
     */
    public class ExampleStateSystemAnalysisModule extends TmfStateSystemAnalysisModule {

        /**
         * Module ID
         */
        public static final String ID = "org.eclipse.tracecompass.examples.state.system.module"; //$NON-NLS-1$

        @Override
        protected @NonNull ITmfStateProvider createStateProvider() {
            return new ExampleStateProvider(Objects.requireNonNull(getTrace()));
        }

    }

## Mipmap feature

The mipmap feature allows attributes to be inserted into the state
system with additional computations performed to automatically store
sub-attributes that can later be used for statistical operations. The
mipmap has a resolution which represents the number of state attribute
changes that are used to compute the value at the next mipmap level.

The supported mipmap features are: max, min, and average. Each one of
these features requires that the base attribute be a numerical state
value (int, long or double). An attribute can be mipmapped for one or
more of the features at the same time.

To use a mipmapped attribute in queries, call the corresponding methods
of the static class <a href="#State_System_Operations" class="wikilink"
title=" TmfStateSystemOperations"> TmfStateSystemOperations</a>.

### AbstractTmfMipmapStateProvider

AbstractTmfMipmapStateProvider is an abstract provider class that allows
adding features to a specific attribute into a mipmap tree. It extends
AbstractTmfStateProvider.

If a provider wants to add mipmapped attributes to its tree, it must
extend AbstractTmfMipmapStateProvider and call modifyMipmapAttribute()
in the event handler, specifying one or more mipmap features to compute.
Then the structure of the attribute tree will be:

    |- <attribute>
    |   |- <mipmapFeature> (min/max/avg)
    |   |   |- 1
    |   |   |- 2
    |   |   |- 3
    |   |  ...
    |   |   |- n (maximum mipmap level)
    |   |- <mipmapFeature> (min/max/avg)
    |   |   |- 1
    |   |   |- 2
    |   |   |- 3
    |   |  ...
    |   |   |- n (maximum mipmap level)
    |  ...
