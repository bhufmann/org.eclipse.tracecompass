# CTF Parser

## CTF Format

CTF is a format used to store traces. It is self defining, binary and
made to be easy to write to. Before going further, the full
specification of the CTF file format can be found at
<http://www.efficios.com/> .

For the purpose of the reader some basic description will be given. A
CTF trace typically is made of several files all in the same folder.

These files can be split into two types:

- Metadata
- Event streams

### Metadata

The metadata is either raw text or packetized text. It is TSDL encoded.
it contains a description of the type of data in the event streams. It
can grow over time if new events are added to a trace but it will never
overwrite what is already there.

### Event Streams

The event streams are a file per stream per cpu. These streams are
binary and packet based. The streams store events and event information
(ie lost events) The event data is stored in headers and field payloads.

So if you have two streams (channels) "channel1" and "channel2" and 4
cores, you will have the following files in your trace directory:
"channel1_0" , "channel1_1" , "channel1_2" , "channel1_3" , "channel2_0"
, "channel2_1" , "channel2_2" & "channel2_3"

## Reading a trace

In order to read a CTF trace, two steps must be done.

- The metadata must be read to know how to read the events.
- the events must be read.

The metadata is a written in a subset of the C language called TSDL. To
read it, first it is depacketized (if it is not in plain text) then the
raw text is parsed by an antlr grammar. The parsing is done in two
phases. There is a lexer (CTFLexer.g) which separated the metatdata text
into tokens. The tokens are then pattern matched using the parser
(CTFParser.g) to form an AST. This AST is walked through using
"IOStructGen.java" to populate streams and traces in trace parent
object.

When the metadata is loaded and read, the trace object will be populated
with 3 items:

- the event definitions available per stream: a definition is a
  description of the datatype.
- the event declarations available per stream: this will save
  declaration creation on a per event basis. They will all be created in
  advance, just not populated.
- the beginning of a packet index.

Now all the trace readers for the event streams have everything they
need to read a trace. They will each point to one file, and read the
file from packet to packet. Every time the trace reader changes packet,
the index is updated with the new packet's information. The readers are
in a priority queue and sorted by timestamp. This ensures that the
events are read in a sequential order. They are also sorted by file name
so that in the eventuality that two events occur at the same time, they
stay in the same order.

## Seeking in a trace

The reason for maintaining an index is to speed up seeks. In the case
that a user wishes to seek to a certain timestamp, they just have to
find the index entry that contains the timestamp, and go there to
iterate in that packet until the proper event is found. this will reduce
the searches time by an order of 8000 for a 256k packet size (kernel
default).

## Interfacing to TMF

The trace can be read easily now but the data is still awkward to
extract.

### CtfLocation

A location in a given trace, it is currently the timestamp of a trace
and the index of the event. The index shows for a given timestamp if it
is the first second or nth element.

### CtfTmfTrace

The CtfTmfTrace is a wrapper for the standard CTF trace that allows it
to perform the following actions:

- **initTrace()** create a trace
- **validateTrace()** is the trace a CTF trace?
- **getLocationRatio()** how far in the trace is my location?
- **seekEvent()** sets the cursor to a certain point in a trace.
- **readNextEvent()** reads the next event and then advances the cursor
- **getTraceProperties()** gets the 'env' structures of the metadata

### CtfIterator

The CtfIterator is a wrapper to the CTF file reader. It behaves like an
iterator on a trace. However, it contains a file pointer and thus cannot
be duplicated too often or the system will run out of file handles. To
alleviate the situation, a pool of iterators is created at the very
beginning and stored in the CtfTmfTrace. They can be retried by calling
the GetIterator() method.

### CtfIteratorManager

Since each CtfIterator will have a file reader, the OS will run out of
handles if too many iterators are spawned. The solution is to use the
iterator manager. This will allow the user to get an iterator. If there
is a context at the requested position, the manager will return that
one, if not, a context will be selected at random and set to the correct
location. Using random replacement minimizes contention as it will
settle quickly at a new balance point.

### CtfTmfContext

The CtfTmfContext implements the ITmfContext type. It is the CTF
equivalent of TmfContext. It has a CtfLocation and points to an iterator
in the CtfTmfTrace iterator pool as well as the parent trace. it is made
to be cloned easily and not affect system resources much. Contexts
behave much like C file pointers (FILE\*) but they can be copied until
one runs out of RAM.

### CtfTmfTimestamp

The CtfTmfTimestamp take a CTF time (normally a long int) and outputs
the time formats it as a TmfTimestamp, allowing it to be compared to
other timestamps. The time is stored with the UTC offset already
applied. It also features a simple toString() function that allows it to
output the time in more Human readable ways:
"yyyy/mm/dd/hh:mm:ss.nnnnnnnnn ns" for example. An additional feature is
the getDelta() function that allows two timestamps to be substracted,
showing the time difference between A and B.

### CtfTmfEvent

The CtfTmfEvent is an ITmfEvent that is used to wrap event declarations
and event definitions from the CTF side into easier to read and parse
chunks of information. It is a final class with final fields made to be
newed very often without incurring performance costs. Most of the
information is already available. It should be noted that one type of
event can appear called "lost event" these are synthetic events that do
not exist in the trace. They will not appear in other trace readers such
as babeltrace.

### Other

There are other helper files that format given events for views, they
are simpler and the architecture does not depend on them.

### Limitations

For the moment live CTF trace reading is not supported.
