# OS Execution Graph Extension

The execution graph is an analysis of some *worker* status and the
relations between them. A *worker* is any object in the model acting
during the execution. For a typicaly operating system analysis, the
workers are the threads.

The Linux Kernel Plugin provides a base execution graph, obtained by
kernel events and tracking the running state of processes, who/what
wakes who, network traffic and communication between threads, etc. But
that analysis may not contain all the possible relations between threads
or may miss some information that are only available in userspace. For
example, traces in a virtual machines experiment may contain additional
events to get the relation between the machines. And spin locks are a
kind of lock that blocks a thread but are not visible from the kernel
only.

The operating system execution graph can thus be extended by plugin who
have additional information to add to the graph.

## Write the graph extension

To extend the execution graph, the plugin must first add the
**org.eclipse.tracecompass.analysis.os.linux.core** plugin to its
dependencies. Then one needs to write a class that extends the
**AbstracTraceEventHandler** and another small one, possibly inline,
implementing **IOsExecutionGraphHandlerBuilder**, to build the handler.

The **handleEvent** method is the one to override in the event handler.
The following code snippet show an example class to extend the graph:

```java
   public class PThreadLockGraphHandler extends AbstractTraceEventHandler {

       /**  
        * Constructor  
        *  
        * @param provider  
        *            The graph provider  
        * @param priority  
        *            The priority of this handler  
        */  
       public PThreadLockGraphHandler(OsExecutionGraphProvider provider, int priority) {  
           super(priority);  
           fProvider = provider;  
           fLastRequest = HashBasedTable.create();  
       }

       /**  
        * The handler builder for the event context handler  
        */  
       public static class HandlerBuilderPThreadLock implements IOsExecutionGraphHandlerBuilder {

           @Override  
           public ITraceEventHandler createHandler(@NonNull OsExecutionGraphProvider provider, int priority) {  
               return new PThreadLockGraphHandler(provider, priority);  
           }  
       }

       private OsWorker getOrCreateKernelWorker(ITmfEvent event, Integer tid) {  
           HostThread ht = new HostThread(event.getTrace().getHostId(), tid);  
           OsWorker worker = fProvider.getSystem().findWorker(ht);  
           if (worker != null) {  
               return worker;  
           }  
           worker = new OsWorker(ht, "kernel/" + tid, event.getTimestamp().getValue()); //$NON-NLS-1$  
           worker.setStatus(ProcessStatus.RUN);  
           fProvider.getSystem().addWorker(worker);  
           return worker;  
       }

       @Override  
       public void handleEvent(ITmfEvent event) {  
           String name = event.getName();  
           if ("myevent".equals(name)) {  
               // Get the TID and corresponding worker  
               Integer tid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);  
               if (tid == null) {  
                   return;  
               }  
               OsWorker worker = getOrCreateKernelWorker(event, tid);  
               // Get the graph to update  
               TmfGraph graph = fProvider.getAssignedGraph();  
               // Create a new vertex at the time of the event to add to the graph  
               TmfVertex vertex = new TmfVertex(event.getTimestamp().toNanos());  
               // The following code shows different possibilities for the graph  
               // Append the vertex to the worker and create an horizontal edge of a specific type  
               graph.append(worker, vertex, EdgeType.BLOCKED);  
               // To create a relation between 2 workers, one needs another vertex  
               // TmfVertex otherVertex = getOriginVertexForThisEvent([...]);  
               // otherVertex.linkVertical(vertex);  
           }  
       }  
   }
```

This class typically has all the logic it needs to retrieve information
on the relations between threads. It will create vertices at any
location of interest for a worker. Those vertices can be appended to the
graph. The class may keep this vertex for future use in a link when the
worker that acts in response to this action arrives.

Note that since many classes may add vertices to the graph, it is
recommended to only append vertices at the current timestamp, as
otherwise, it may add vertices at times earlier than the last vertex.

## Adding the extension point

To advertise this extension to the execution graph, the following
extension should be added in the plugin:

```xml
   <extension
         point="org.eclipse.tracecompass.analysis.os.linux.core.graph.handler">  
       <handler
           class="org.eclipse.tracecompass.incubator.internal.lttng2.ust.extras.core.pthread.PThreadLockGraphHandler$HandlerBuilderPThreadLock"
            priority="10">  
       </handler>  
   </extension>
```

The *class* attribute links to the handler builder class and the
*priority* attribute indicates at which priority the handler will be
registered. A handler with a lower priority will be executed before a
handler with a higher one. The default priority is 10 and this is the
priority at which the kernel graph itself is built.
