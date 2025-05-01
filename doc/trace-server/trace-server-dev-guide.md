# Introduction

The purpose of Trace Compass trace server is to facilitate the integration of trace analysis and visualization using client applications that implement the `Trace Server Protocol`, for example, the `vscode-trace-extension` or `tsp-python-client`.

This guide goes over the internal components of the Trace Compass framework and how to add . It should help developers trying to add new capabilities (support for new trace type, new analysis or views, etc.) to the framework. End-users, using the RCP for example, should not have to worry about the concepts explained here.

## Key Concepts

### Trace

A trace is the integral part for the trace visualization. A trace consists of trace events. Each trace event has a timestamp and payload. The format of the a trace is trace type specific. A parsers needs to available in the trace server for each trace type.

### Experiment

An experiment consisits of a on or more traces. Trace events are sorted in chronological order.

### Generic State system

A generic `state system` is a utility available in Trace Compass to track different states over the duration of a trace. A state system may be persisted to disk as `state history` as interval tree data structure.

### Segment Store

A segment store is similar to a state system, but it allows overlapping intervals. This is useful to store any kind of intervals (e.g. latencies) over the duration of a trace.

### Analysis
An `analysis` module in Trace Compass is an entity that consumes trace events of a trace and/or experiment, performs some computation, for example CPU or memory usage, use state machines to track analayse states of a system, follows key characterisics stored trace events and much more. `analysis` modules may persit (e.g. using a state system) the results on disk so that those computations don't need to be re-done every time the data is requested.

### Data Provider (Output)

A `data provider` or `output` the part that provides the data for visualizations. Each `experiment` will have advertise what data providers are available for a given experiment. A data provider may or may not use an `analysis` module.

### Provider Type

The `provider type` determines what is the data structures the `data provider` returns and which methods are available to query the data provider. Such methods will have corresponding trace server endpoints.

### Data Provider Descriptor (Output Descriptor)

This descriptor describes the data provider. This is information that clients will use to determine which endpoints (methods) to call, what data structures to expect, what capabilities the data provider has (e.g. can it be deleted or can it create derived data providers). It also has a name and description. Each data provider might have children data provider, which are either derived or just grouped together.

## Data Provider Factory

A data provider factory purpose is to create data provider instances that can be queried for the actual data structures. The factory has a method to return `data provider descriptors` that describe the data providers that the factory can create. A `data provider factory` can also be are `data provider configurator` or provide a separate data provider configuator class to be used for data provider configurations.

### Data Provider Configurator

A `data provider configurator` provides methods to create and delete derived data providers using a configuration. This can be used to parameterize an existing data provider, for example, cpu usage for given processes only.

### Data Provider Manager

The `data provider manager` is a core entity in Trace Compass that can be used to manage data providers and it's factories. It provides utilities to get all available data providers (descriptors), get or create data provider instances, register and derigister factories programatically, remove data provider instance and more.

### Configuration

A `configuration` is the data structure to configure and customize the trace server back-end globaly (e.g. load XML analysis) or to configure data providers with parameters.


## Trace Compass Server Workspace

The Trace Compass workspace structure is based on the workspace of Eclipse platform. The workspace structure is an organized system that the Eclipse 
platform uses to manage `projects`, `settings`, and `metadata`. 

Trace Compass defines so-called tracing projects where trace files, experiments and other analysis persistent information is stored. By default, there is one tracing project called 'Tracing'. While it's possible to have multiple tracing projects the Trace Compass server has no API to create other tracing projects. The workspace structure of the `Trace Compass server` is the same as the workspace structure of the classic Trace Compass RCP application.

The following chapters describe the workspace of the Trace Compass trace server application. This folder structure is created when starting the server and should not be updated manually!

```python
<Workspace Root>                                       # Workspace root
  ├── .log/                                            # The error log
  ├── .lock/                                           # Lock file (when server is running)
  ├── .metadata                                        # Stores internal configuration and metadata.
      ├── .plugins/                                    # Plugin-specific data
      │       ├── org.eclipse.tracecompass.tmf.analysis.xml.core # xml.core plugin specific data
      │       │   └── xml_files                        # root folder for xml files
      │       │       ├── my-custom-analysis.xml       # example xml analysis   
      |       |       ├── ...
      │       ├── org.eclipse.tracecompass.tmf.core    # tmf.core plugin specific data
      │       │   ├── dataprovider.ini                 # file to hide/show data providers
      │       │   ├── markers.xml                      # definition of overlay markers
      │       │   └── markers.xsd                      # xsd for markers.xml 
      |       ├── org.eclipse.core.runtime             # Platform runtime configuraiton files
      |       │   └── .settings
      |       │       ├── org.eclipse.tracecompass.tmf.analysis.xml.core.prefs # xml.core preferences
      |       |       ├── org.eclipse.tracecompass.tmf.core.prefs              # tmf.core preferences 
              |       ├── ...
      │       ├── org.eclipse.core.resources           # Eclipse platform file system resources related files
      │       │   ├── .projects
      │       │   │   └── Tracing                      # Platform files for Tracing project
      │       │   │       ├── .indexes                 # Resources indexes
      |       |   |       ├── ... 
      |       |   ├── ...
      |       ├── ...   

```

### Workspace Root
This is the main folder where all Trace Compass projects, settings, and metadata are stored. By default, the `Trace Compass server` 
creates the workspace in the user's home folder under the name `.tracecompass-webapp` (e.g. `/home/<username>/.tracecompass-webapp`). 

One can change the workspace location when starting the Trace Compass server, by adding command-line parameter `-data <new path>` or updating the 
`tracecompass.ini` file of the server download package.

### `.metadata` Folder

This folder stores internal configuation and metadata. It will contain the following important files and directories (amongst others):

- `.log`: Contains the error logs
- `.lock`: Lock file indicating that workspace is in-use. Should only be there if server application is running. 
- `.plugins`: Each plug-in of the application may store configuration files under a folder with its plug-in name. For example, `org.eclipse.tracecompass.core` will store marker.xml, dataprovider.ini and other files. Those are internal and should not be updated manually.

### `Tracing` Project folder

There is only one project folder for the `Trace Compass server` with the name `Tracing`. It has the following structure:

```python
Tracing
  ├── .project                                                # Project metadata
  ├── Traces                                                  # Traces root folder
  |  ├── /<path-to-trace1>/<sym-link-name-of-trace1>          # Trace link <name-of-trace1>
  |  ├── /<path-to-trace2>/<sym-link-name-of-trace2>          # Trace link for <name-of-trace2>
  ├── Experiments                                             # Experiments root folder
  |  ├── <experiment-name>                                    # Folder for experiment with name <experiment-name>
  |      ├── /<path-to-trace1>/<name-of-trace1>               # Trace path, matching path in Traces
  |      ├── /<path-to-trace2>/<name-of-trace2>               # Trace path, matching path in Traces
  ├── .tracing                                                # Supplementary folder
      ├── <experiment-name>-exp                               # Experiment specific supplementary folder
      |   ├── checkpoint_btree.idx                            # Experiment Btree checkpoint index
      |   ├── checkpoint_flatarray.idx                        # Experiment Flat array checkpoint index
      |   ├── <exp-analysis-state-system1.ht>                 # A state system
      |   ├── <...>                                           # config files and directories
      ├──  /<path-to-trace1>/<name-of-trace1>                 # Trace specific supplementary folder, matching path in Traces
      |                    ├── checkpoint_btree.idx           # Experiment Btree checkpoint index
      |                    ├── checkpoint_flatarray.idx       # Experiment Flat Array checkpoint index
      |                    ├── <analysis-state-system1.ht>    # A state system
      |                    ├── <analysis-state-system2.ht>    # Another state system
      |                    ├── <...>                          # More supplementary files and folders
      ├── /<path-to-trace2>/<name-of-trace2>                  # Trace supplementary folder, matching path in Traces
                           ├── checkpoint_btree.idx           # Experiment Btree checkpoint index
                           ├── checkpoint_flatarray.idx       # Experiment Flat Array checkpoint index
                           ├── <analysis-state-system1.ht>    # A state system
                           ├── <analysis-state-system2.ht>    # Another state system
                           ├── <...>                          # More supplementary files and folders
```

This folder structure is created and updated when opening a trace, experiment or configuring data providers. It's cleaned-up by the application when deleting a trace, experiment or configuration. This should not be updated manually!

#### `Traces` Folder

The `Traces` folder structure determines which traces are available on the server. To be unique, the path matches the file system path of the trace. The trace name (e.g. `name-of-trace1`) is a symlink to the trace file (or trace folder) in the file system. 

#### `Experiments` Folder
An experiment is identified by the experiment name in the `Experiment` folder (e.g. `experiment-name`). The contained traces are identified by the path to the trace matching the path in the `Traces` folder.

#### Supplementary `.tracing` Folder
Each experiment has a supplementary folder under the `.tracing` folder for experiment related data (e.g. experiment index, state systems, configuration files etc.). The name of the experiment supplementary folder is `<experiment-name>` with suffix `-exp`. 

Each trace has also a supplementary folder under the `tracing` folder. The path of the trace supplementary folder matches the of trace under the `Traces` folder. 

#### How to find the supplementary folder for a trace or experiment
When a trace is openend or a experiment is created the supplementary folder is created by the server application. It stores the path into persistent property of the `org.eclipse.core.resources.IFolder` representing the trace in the workspace.

```java
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;

    //...
    private static synchronized boolean createResource(String path, IResource resource) throws CoreException {

        // create the resource hierarchy.
        IPath targetLocation = new org.eclipse.core.runtime.Path(path);
        createFolder((IFolder) resource.getParent(), null);
        if (!ResourceUtil.createSymbolicLink(resource, targetLocation, true, null)) {
            return false;
        }

        // create supplementary folder on file system:
        IFolder supplRootFolder = resource.getProject().getFolder(TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER_NAME);
        IFolder supplFolder = supplRootFolder.getFolder(resource.getProjectRelativePath().removeFirstSegments(1));
        createFolder(supplFolder, null);
        resource.setPersistentProperty(TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER, supplFolder.getLocation().toOSString());

        return true;
    }
    //...
```

To retieve the supplementary one can read the persistent property directy from the resource, or use the TmfTraceManager class to get it from the `ITmfTrace` object representing a trace or experiment.

```java
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;

    //...
        // Trace
        ITmfTrace trace = getTrace();
        String supplPath = TmfTraceManager.getSupplementaryFileDir(trace);

        // Experiment
        TmfExperiment experiment = getExperiment();
        String expSupplPath = TmfTraceManager.getSupplementaryFileDir(experiment);
   //...
```

Note: 
- If the `trace.getResource()` returns null, `TmfTraceManager.getSupplementaryFileDir(trace)` will create a temporary folder under the OS's temp folder (for Linux /tmp/) and returns this directory. This folder will be temporary and every call with a null resource it will create a new one. Hence, it's not suitable for persistence.
- Always, use the Eclipse platform API to create resources and to set the persistence property.

## How to create a custom trace server

To create a custom trace server based on Trace Compass, a custom Eclipse RCP application has to be defined that includes all relevant Trace Compass core plug-ins, core extensions (e.g. lttng.core, profiling.core) and all custom plug-ins for custom trace parsing and analysis. Don't include any plug-ins that requires UI. The best way to start is to copy the following Trace Compass server plug-in projects and modify the content. 

TODO

## How to create a analysis module
To create analysis modules using the `Analysis Framework` follow the instructions in the Trace Compass developer guide. Those analysis modules are applicable to all traces of given trace type or all trace types. The can be executed automatically when open a trace or can be executed manually when opening a output.

## How to take advantage of Analysis Framework

TODO utilites

## How to create a data provider

The Trace Compass server and the Trace Server Protocol is not aware of the concept `Analysis` in contrary to the Eclipse Trace Compass RCP application. The Trace Compass server however, uses data providers to visualize analysis results. To create a data provider follow the instructions in the Trace Compass developer guide.

These instructions are not enough to make the data providers visible over the Trace Server Protocol (TSP). Each data provider factory created in through those instructions has to implement the `IDataProviderFactory.getDescriptor(ITmfTrace)` method which returns a list of data provider descriptors that this factory can instantiate.

```java
(TODO)
```

Once the getDescripter(ITmfTrace) method is implemented, then the `DataProviderManager` can be used to fetch all avaliable data providers of a trace or experiment.

```java
    //...
       ITmfTrace trace = getTrace();
       List<IDataProviderDescriptors> descriptors = DataProviderManager.getAvailableProviders(trace);
    //...
```

The Trace Compass server will use this method for the corresponding endpoint to get all available data providers. (TODO)

```java
// TODO
```

## How to implement configurable data provider

Defining data providers statically as described in previous chapter is not always flexible enough for user's needs. It's often required to create derived data provider from an existing data provider. For example, it might be interesting to derive CPU usage data provider from the original CPU usage data provider, that shows the CPU usage for a given CPU. Another use case to derive a virtual table data provider from the events table data provider, that shows only trace events of with a certain events type.

Using data provider configurators this is possible. 


### Containment versus inheritance

## How to register a data provider factory




