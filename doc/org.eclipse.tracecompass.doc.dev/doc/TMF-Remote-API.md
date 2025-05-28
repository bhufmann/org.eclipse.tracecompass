# TMF Remote API

The TMF remote API is based on the remote services implementation of the
Eclipse CDT project. It comes with a built-in SSH implementation based
JSch as well as with support for a local connection. The purpose of this
API is to provide a programming interface to the CDT remote services
implementation for connection handling, command-line execution and file
transfer handling. It provides utility functions to simplify repetitive
tasks.

The TMF Remote API can be used for remote trace control, fetching of
traces from a remote host into the Eclipse Tracing project or uploading
files to the remote host. For example, the LTTng tracer control feature
uses the TMF remote API to control an LTTng host remotely and to
download corresponding traces.

In the following chapters the relevant classes and features of the TMF
remote API is described.

## Prerequisites

To use the TMF remote API one has to add the relevant plug-in
dependencies to a plug-in project. To create a plug-in project see
[Creating an Eclipse UI Plug-in](View-Tutorial.md#creating-an-eclipse-ui-plug-in)

To add plug-in dependencies double-click on the MANIFEST.MF file. Change
to the Dependencies tab and select **Add...** of the *Required Plug-ins*
section. A new dialog box will open. Next find plug-in
*org.eclipse.tracecompass.tmf.remote.core* and press **OK**. Follow the
same steps, add *org.eclipse.remote.core*. If UI elements are needed in
the plug-in also add *org.eclipse.tracecompass.tmf.remote.ui* and
*org.eclipse.remote.ui*.

## TmfRemoteConnectionFactory

This class is a utility class for creating *IRemoteConnection* instances
of CDT programatically. It also provides access methods to the OSGI
remote services of CDT.

### Accessing the remote services manager (OSGI service)

The main entry point into the CDT remote services system is the
*IRemoteServicesManager* OSGI service. It provides a list of connection
types and the global list of all connections.

To access an OSGI service, use the method **getService()** of the
**TmfRemoteConnectionFactory** class:

```java
    IRemoteServicesManager manager = TmfRemoteConnectionFactory.getService(IRemoteServicesManager.class);
```

### Obtaining a IRemoteConnection

To obtain an **IRemoteConnection** instance use the method
**TmfRemoteConnectionFactory.getRemoteConnection(String
remoteServicesId, String name)**, where *remoteServicesId* is the ID of
service ID for the connection, and *name* the name of the connection.
For built-in SSH the *remoteServicesId* is "org.eclipse.remote.JSch".

```java
    IRemoteConnection connection = TmfRemoteConnectionFactory.getRemoteConnection("org.eclipse.remote.JSch", "My Connection");
```

Note that the connection needs to be created beforehand using the Remote
Connection wizard implementation (**Window -> Preferences -> Remote
Development -> Remote Connection**) in the Eclipse application that
executes this plug-in. For more information about creating connections
using the Remote Connections feature of CDT refer to
[link](https://github.com/eclipse-cdt/cdt/tree/main/remote/org.eclipse.remote.doc.isv).
Alternatively it can be created programmatically using the corresponding
API of TMF [Creating an IRemoteConnection instance](#creating-an-iremoteconnection-instance).

To obtain an **IRemoteConnection** instance use method
**TmfRemoteConnectionFactory.getLocalConnection()**.

```java
    IRemoteConnection connection = TmfRemoteConnectionFactory.getLocalConnection();
```

### Creating an IRemoteConnection instance

It is possible to create an **IRemoteConnection** instance
programmatically using the **TmfRemoteConnectionFactory**. Right now
only build-in SSH or Local connection is supported.

To create an **IRemoteConnection** instance use the method
**createConnection(URI hostURI, String name)** of class
**TmfRemoteConnectionFactory**, where *hostURI* is the URI of the remote
connection, and *name* the name of the connection. For a built-in SSH
use:

```java
    import org.eclipse.remote.core.IRemoteConnection;
    ...
        try {
            URI hostUri = URIUtil.fromString("ssh://userID@127.0.0.1:22");
            IRemoteConnection connection = TmfRemoteConnectionFactory.createConnection(hostUri, "MyHost");
        } catch (URISyntaxException e) {
            return new Status(IStatus.ERROR, "my.plugin.id", "URI syntax error", e);
        } catch (RemoteConnectionException e) {
            return new Status(IStatus.ERROR, "my.plugin.id", "Connection cannot be created", e);
        }
    ...
```

Note that if a connection already exists with the given name then this
connection will be returned.

### Providing a connection factory

Right now only build-in SSH or Local connection of PTP is supported. If
one wants to provide another connection factory with a different remote
service implementation use the interface **IConnectionFactory** to
implement a new connection factory class. Then, register the new factory
to **TmfRemoteConnectionFactory** using method
**registerConnectionFactory(String connectionTypeId, IConnectionFactory
factory)**, where *connectionTypeId* is a unique ID and *factory* is the
corresponding connection factory implementation.

## RemoteSystemProxy

The purpose of the RemoteSystemProxy is to handle the connection state
of **IRemoteConnection** (connect/disconnect). Before opening a
connection it checks if the connection had been open previously. If it
was open, disconnecting the proxy will not close the connection. This is
useful if multiple components using the same connection at the same time
for different features (e.g. Tracer Control and remote fetching of
traces) without impacting each other.

### Creating a RemoteSystemProxy

Once one has an **IRemoteConnection** instance a **RemoteSystemProxy**
can be constructed by:

```java
    // Get local connection (for example)
    IRemoteConnection connection = TmfRemoteConnectionFactory.getLocalConnection();
    RemoteSystemProxy proxy = new RemoteSystemProxy(connection);
```

### Opening the remote connection

To open the connection call method **connect()**:

```java
        proxy.connect();
```

This will open the connection. If the connection has been previously
opened then it will immediately return.

### Closing the remote connection

To close the connection call method **disconnect()**:

```java
        proxy.disconnect();
```

Note: This will close the connection if the connection was opened by
this proxy. Otherwise it will stay open.

### Disposing the remote connection

If a remote system proxy is not needed anymore the proxy instance needs
to be disposed by calling method **dispose()**. This may close the
connection if the connection was opened by this proxy. Otherwise it will
stay open.

```java
        proxy.dispose();
```

### Checking the connection state

To check the connection state use method **isConnected()** of the
**RemoteSystemProxy** class.

```java
        if (proxy.isConnected()) {
            // do something
        }
```

### Retrieving the IRemoteConnection instance

To retrieve the **IRemoteConnection** instance use the
**getRemoteConnection()** method of the **RemoteSystemProxy** class.
Using this instance relevant features of the remote connection
implementation can be accessed, for example remote file service
(**IRemoteFileService**) or remote process service
(**IRemoteProcessService**).

```java
    import org.eclipse.remote.core.IRemoteConnection;
    import org.eclipse.remote.core.IRemoteFileService;
    ...
        IRemoteRemoteConnection connection = proxy.getRemoteConnection();
        IRemoteFileService fileService = connection.getService(IRemoteFileService.class);
        if (fileService != null) {
            // do something (e.g. download or upload a file)
        }

    import org.eclipse.remote.core.IRemoteConnection;
    import org.eclipse.remote.core.IRemoteFileService;
    ...
        IRemoteRemoteConnection connection = proxy.getRemoteConnection();
        IRemoteFileService processService = connection.getService(IRemoteProcessService.class);
        if (processService != null) {
            // do something (e.g. execute command)
        }
```

### Obtaining a command shell

The TMF remote API provides a Command shell implementation to execute
remote command-line commands. To obtain a command-line shell use the
RemoteSystemProxy.

```java
    import org.eclipse.remote.core.IRemoteConnection;
    import org.eclipse.remote.core.IRemoteFileService;
    import org.eclipse.tracecompass.tmf.remote.core.shell.ICommandShell
    ...
        ICommandShell shell = proxy.createCommandShell();
        ICommandInput command = fCommandShell.createCommand();
        command.add("ls");
        command.add("-l");
        ICommandResult result = shell.executeCommand(command, new NullProgressMonitor);
        System.out.println("Return value: " result.getResult());
        for (String line : result.getOutput()) {
            System.out.println(line);
        }
        for (String line : result.getErrorOutput()) {
            System.err.println(line);
        }
        shell.dispose();
```

Note that the shell needs to be disposed if not needed anymore.

Note for creating a command with parameters using the **CommandInput**
class, add the command and each parameter separately instead of using
one single String.
