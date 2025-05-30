# JUL Logging

Logging can be quite useful to debug a class, see its interactions with
other components and understand the behavior of the system. TraceCompass
uses JUL to log various events in the code, which can then be used to
model and analyze the system's workflow. Here are some guidelines to use
logging efficiently in Trace Compass. See the User Documentation for
instructions on how to enable logging and obtain traces.

## Use a static logger for each class

Each class should define and use their own static logger like this:

```java
    private static final Logger LOGGER = TraceCompassLog.getLogger(StateSystem.class);
```

It is then easy to filter the components to log by their full class
name. The *TraceCompassLog#getLogger* method is a wrapper for
*java.util.logging.Logger#getLogger*, but the Trace Compass's logging
initialization (overriding the default's ConsoleHandler and INFO level
for the org.eclipse.tracecompass namespace when logging is not enabled)
is done in the static initializer of this class. Using the wrapper
method ensures that this code is called and the user will not see
Console message all over the place.

**Note on abstract classes**: It is debatable whether to use a static
logger with the abstract class name or a logger with the concrete
class's name.

In the former case, logging for this class uses the classes's own
namespace, but it is impossible to discriminate logging statement by
concrete classes unless the concrete class name is added as parameter to
the statement (when necessary).

The latter case has the advantage that one can log only the concrete
class and see all that goes on in the abstract class as well, but the
concrete class may be in another namespace and will not benefit from the
*TraceCompassLog* logging initialization and the user will see console
logging happen.

Both methods have their advantages and there is no clear good answer.

## Use a message supplier

A logging statement, to be meaningful, will usually log a string that
contains data from the context and will thus do string concatenation.
This has a non-negligible overhead. To avoid having to do the costly
string concatenation when the statement is not logged, java provides
method taking a *Supplier<String>* as argument and that method should be
used for all logging statements

```java
    LOGGER.info(() -> "[Component:Action] param1=" + myParam1 + ", param2=" + myParam2);
```

## Choose the appropriate log level

The available log levels for JUL are SEVERE, WARNING, INFO, CONFIG,
FINE, FINER, FINEST. The default level when not specified is INFO.

- As a rule of thumb, enabling all INFO level statements should have a
  near zero impact on the execution, so log parameters that require some
  computations, or methods that are called very often should not be
  logged at INFO level.
- CONFIG level should provide more detailed information than the INFO
  level, but still not impact the execution too much. It should be
  possible for a component to use up to CONFIG level statements and make
  meaningful analyses using the timestamps of the events.
- FINE, FINER and FINEST are for statements that will not be used with
  the timestamps. Enabling them may have a visible effect on the
  performance of Trace Compass. They will typically be used with a
  purpose in mind, like debugging a component or getting data on caches
  for examples.

## Log message format

JUL logging will produce trace data and unless one wants to visually
parse a trace one event at a time, it will typically be used with an
analysis to produce a result. To do so, the log messages should have a
format that can then be associated with a trace type.

Third party plugins provide a custom trace parser and LTTng trace type
for JUL statements that use the following format

```
    [EventName:MayContainSemiColon] paramName1=paramValue1, paramName2=paramValue2
```

## Logging to populate Callstacks and Callgraph analyses

In order to log data in a way that the call stack analysis has enough
information to display, use the TraceCompassLogUtils#ScopeLog. It is an
auto-closable logger that will log a try-with-resources block of code.

```java
       try (TraceCompassLogUtils.ScopeLog linksLogger = new TraceCompassLogUtils.ScopeLog(LOGGER, Level.CONFIG, "Perform Query")) { //$NON-NLS-1$
          // Do something
          new Object();
      }`
```

The resulting trace will have the following fields

```
      INFO: {"ts":12345,"ph":"B",tid:1,"name:Perform Query"}
      INFO: {"ts":"12366,"ph":"E","tid":1}
```

## Logging to track Object life cycles

In order to log data so that a lifecycle of a given object can be
followed, use TraceCompassLogUtils#traceObjectCreation and
TraceCompassLogUtils#traceObjectDestruction. The objects life cycles
will be tracked and displayed.

## Logging to track Asynchronous operations

In order to log data so that a lifecycle of a given object can be
followed, use TraceCompassLogUtils#traceObjectCreation and
TraceCompassLogUtils#traceAsyncStart/Nested/End. These create nestable
sequences to follow.

## Analyzing Trace Compass Logs

Trace Compass can be traced by doing the following in the launch
configuration:

- (java 8 only) -vmargs
- -Djava.util.logging.config.file=%gitroot%/logging.properties (where
  %gitroot% is the directory tracecompass is checked out to)
- -Dorg.eclipse.tracecompass.logging=true

Additionally the folder the trace is being written to (default is
\`home/.tracecompass/logs\`) needs to be created in advance. After
running Trace Compass, a \`trace_n.json\` will be created in the tracing
folder. It needs to be converted to true json, so use the \`jsonify.sh\`
script in the root directory to convert it. Then it can be loaded into
Trace Compass, if the **Trace Event format** is installed from the
incubator, or from a web browser such as Chromium.

The performance impact is low enough as long as the log level is greater
than **Level#FINEST**.

NOTE: thread 1 is always the UI thread for Trace Compass. The thread
numbers are the JVM threads and do not correspond necessarily to Process
IDs. For more information, see the **Flame Graph** documentation in the
user guide.
