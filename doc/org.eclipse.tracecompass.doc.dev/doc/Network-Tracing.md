# Network Tracing

## Adding a protocol

Supporting a new network protocol in TMF is straightforward. Minimal
effort is required to support new protocols. In this tutorial, the UDP
protocol will be added to the list of supported protocols.

### Architecture

All the TMF pcap-related code is divided in three projects (not
considering the tests plugins):

- **org.eclipse.tracecompass.pcap.core**, which contains the parser that
  will read pcap files and constructs the different packets from a
  ByteBuffer. It also contains means to build packet streams, which are
  conversation (list of packets) between two endpoints. To add a
  protocol, almost all of the work will be in that project.
- **org.eclipse.tracecompass.tmf.pcap.core**, which contains
  TMF-specific concepts and act as a wrapper between TMF and the pcap
  parsing library. It only depends on org.eclipse.tracecompass.tmf.core
  and org.eclipse.tracecompass.pcap.core. To add a protocol, one file
  must be edited in this project.
- **org.eclipse.tracecompass.tmf.pcap.ui**, which contains all TMF pcap
  UI-specific concepts, such as the views and perspectives. No work is
  needed in that project.

### UDP Packet Structure

The UDP is a transport-layer protocol that does not guarantee message
delivery nor in-order message reception. A UDP packet (datagram) has the
following
[structure](http://en.wikipedia.org/wiki/User_Datagram_Protocol#Packet_structure):

<table>
<thead>
<tr>
<th
style="border-bottom:none; border-right:none;"><p><em>Offsets</em></p></th>
<th style="border-left:none;"><p>Octet</p></th>
<th colspan="8"><p>0</p></th>
<th colspan="8"><p>1</p></th>
<th colspan="8"><p>2</p></th>
<th colspan="8"><p>3</p></th>
</tr>
</thead>
<tbody>
<tr>
<td style="border-top: none"><p>Octet</p></td>
<td><p><code>Bit</code></p></td>
<td><p><code> 0</code></p></td>
<td><p><code> 1</code></p></td>
<td><p><code> 2</code></p></td>
<td><p><code> 3</code></p></td>
<td><p><code> 4</code></p></td>
<td><p><code> 5</code></p></td>
<td><p><code> 6</code></p></td>
<td><p><code> 7</code></p></td>
<td><p><code> 8</code></p></td>
<td><p><code> 9</code></p></td>
<td><p><code>10</code></p></td>
<td><p><code>11</code></p></td>
<td><p><code>12</code></p></td>
<td><p><code>13</code></p></td>
<td><p><code>14</code></p></td>
<td><p><code>15</code></p></td>
<td><p><code>16</code></p></td>
<td><p><code>17</code></p></td>
<td><p><code>18</code></p></td>
<td><p><code>19</code></p></td>
<td><p><code>20</code></p></td>
<td><p><code>21</code></p></td>
<td><p><code>22</code></p></td>
<td><p><code>23</code></p></td>
<td><p><code>24</code></p></td>
<td><p><code>25</code></p></td>
<td><p><code>26</code></p></td>
<td><p><code>27</code></p></td>
<td><p><code>28</code></p></td>
<td><p><code>29</code></p></td>
<td><p><code>30</code></p></td>
<td><p><code>31</code></p></td>
</tr>
<tr>
<td><p>0</p></td>
<td><p><code>0</code></p></td>
<td colspan="16" style="background:#fdd;"><p>Source port</p></td>
<td colspan="16"><p>Destination port</p></td>
</tr>
<tr>
<td><p>4</p></td>
<td><p><code>32</code></p></td>
<td colspan="16"><p>Length</p></td>
<td colspan="16" style="background:#fdd;"><p>Checksum</p></td>
</tr>
</tbody>
</table>

Knowing that, we can define an UDPPacket class that contains those
fields.

### Creating the UDPPacket

First, in org.eclipse.tracecompass.pcap.core, create a new package named
**org.eclipse.tracecompass.pcap.core.protocol.name** with name being the
name of the new protocol. In our case name is udp so we create the
package **org.eclipse.tracecompass.pcap.core.protocol.udp**. All our
work is going in this package.

In this package, we create a new class named UDPPacket that extends
Packet. All new protocol must define a packet type that extends the
abstract class Packet. We also add different fields:

- *Packet* **fChildPacket**, which is the packet encapsulated by this
  UDP packet, if it exists. This field will be initialized by
  findChildPacket().
- *ByteBuffer* **fPayload**, which is the payload of this packet.
  Basically, it is the UDP packet without its header.
- *int* **fSourcePort**, which is an unsigned 16-bits field, that
  contains the source port of the packet (see packet structure).
- *int* **fDestinationPort**, which is an unsigned 16-bits field, that
  contains the destination port of the packet (see packet structure).
- *int* **fTotalLength**, which is an unsigned 16-bits field, that
  contains the total length (header + payload) of the packet.
- *int* **fChecksum**, which is an unsigned 16-bits field, that contains
  a checksum to verify the integrity of the data.
- *UDPEndpoint* **fSourceEndpoint**, which contains the source endpoint
  of the UDPPacket. The UDPEndpoint class will be created later in this
  tutorial.
- *UDPEndpoint* **fDestinationEndpoint**, which contains the destination
  endpoint of the UDPPacket.
- *ImmutableMap\<String, String\>* **fFields**, which is a map that
  contains all the packet fields (see in data structure) which assign a
  field name with its value. Those values will be displayed on the UI.

We also create the UDPPacket(PcapFile file, @Nullable Packet parent,
ByteBuffer packet) constructor. The parameters are:

- *PcapFile* **file**, which is the pcap file to which this packet
  belongs.
- *Packet* **parent**, which is the packet encasulating this UDPPacket
- *ByteBuffer* **packet**, which is a ByteBuffer that contains all the
  data necessary to initialize the fields of this UDPPacket. We will
  retrieve bytes from it during object construction.

The following class is obtained:

    package org.eclipse.tracecompass.pcap.core.protocol.udp;

    import java.nio.ByteBuffer;
    import java.util.Map;

    import org.eclipse.tracecompass.internal.pcap.core.endpoint.ProtocolEndpoint;
    import org.eclipse.tracecompass.internal.pcap.core.packet.BadPacketException;
    import org.eclipse.tracecompass.internal.pcap.core.packet.Packet;

    public class UDPPacket extends Packet {

        private final @Nullable Packet fChildPacket;
        private final @Nullable ByteBuffer fPayload;

        private final int fSourcePort;
        private final int fDestinationPort;
        private final int fTotalLength;
        private final int fChecksum;

        private @Nullable UDPEndpoint fSourceEndpoint;
        private @Nullable UDPEndpoint fDestinationEndpoint;

        private @Nullable ImmutableMap<String, String> fFields;

        /**
         * Constructor of the UDP Packet class.
         *
         * @param file
         *            The file that contains this packet.
         * @param parent
         *            The parent packet of this packet (the encapsulating packet).
         * @param packet
         *            The entire packet (header and payload).
         * @throws BadPacketException
         *             Thrown when the packet is erroneous.
         */
        public UDPPacket(PcapFile file, @Nullable Packet parent, ByteBuffer packet) throws BadPacketException {
            super(file, parent, PcapProtocol.UDP);
            // TODO Auto-generated constructor stub
        }


        @Override
        public Packet getChildPacket() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ByteBuffer getPayload() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean validate() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        protected Packet findChildPacket() throws BadPacketException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ProtocolEndpoint getSourceEndpoint() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ProtocolEndpoint getDestinationEndpoint() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Map<String, String> getFields() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getLocalSummaryString() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        protected String getSignificationString() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return 0;
        }

    }

Now, we implement the constructor. It is done in four steps:

- We initialize fSourceEndpoint, fDestinationEndpoint and fFields to
  null, since those are lazy-loaded. This allows faster construction of
  the packet and thus faster parsing.
- We initialize fSourcePort, fDestinationPort, fTotalLength, fChecksum
  using ByteBuffer packet. Thanks to the packet data structure, we can
  simply retrieve packet.getShort() to get the value. Since there is no
  unsigned in Java, special care is taken to avoid negative number. We
  use the utility method ConversionHelper.unsignedShortToInt() to
  convert it to an integer, and initialize the fields.
- Now that the header is parsed, we take the rest of the ByteBuffer
  packet to initialize the payload, if there is one. To do this, we
  simply generate a new ByteBuffer starting from the current position.
- We initialize the field fChildPacket using the method
  findChildPacket()

The following constructor is obtained:

        public UDPPacket(PcapFile file, @Nullable Packet parent, ByteBuffer packet) throws BadPacketException {
            super(file, parent, Protocol.UDP);

            // The endpoints and fFields are lazy loaded. They are defined in the get*Endpoint()
            // methods.
            fSourceEndpoint = null;
            fDestinationEndpoint = null;
            fFields = null;

            // Initialize the fields from the ByteBuffer
            packet.order(ByteOrder.BIG_ENDIAN);
            packet.position(0);

            fSourcePort = ConversionHelper.unsignedShortToInt(packet.getShort());
            fDestinationPort = ConversionHelper.unsignedShortToInt(packet.getShort());
            fTotalLength = ConversionHelper.unsignedShortToInt(packet.getShort());
            fChecksum = ConversionHelper.unsignedShortToInt(packet.getShort());

            // Initialize the payload
            if (packet.array().length - packet.position() > 0) {
                byte[] array = new byte[packet.array().length - packet.position()];
                packet.get(array);

                ByteBuffer payload = ByteBuffer.wrap(array);
                payload.order(ByteOrder.BIG_ENDIAN);
                payload.position(0);
                fPayload = payload;
            } else {
                fPayload = null;
            }

            // Find child
            fChildPacket = findChildPacket();

        }

Then, we implement the following methods:

- *public Packet* **getChildPacket()**: simple getter of fChildPacket
- *public ByteBuffer* **getPayload()**: simple getter of fPayload
- *public boolean* **validate()**: method that checks if the packet is
  valid. In our case, the packet is valid if the retrieved checksum
  fChecksum and the real checksum (that we can compute using the fields
  and payload of UDPPacket) are the same.
- *protected Packet* **findChildPacket()**: method that create a new
  packet if a encapsulated protocol is found. For instance, based on the
  fDestinationPort, it could determine what the encapsulated protocol is
  and creates a new packet object.
- *public ProtocolEndpoint* **getSourceEndpoint()**: method that
  initializes and returns the source endpoint.
- *public ProtocolEndpoint* **getDestinationEndpoint()**: method that
  initializes and returns the destination endpoint.
- *public Map\<String, String\>* **getFields()**: method that
  initializes and returns the map containing the fields matched to their
  value.
- *public String* **getLocalSummaryString()**: method that returns a
  string summarizing the most important fields of the packet. There is
  no need to list all the fields, just the most important one. This will
  be displayed on UI.
- *protected String* **getSignificationString()**: method that returns a
  string describing the meaning of the packet. If there is no particular
  meaning, it is possible to return getLocalSummaryString().
- public boolean'' **equals(Object obj)**: Object's equals method.
- public int'' **hashCode()**: Object's hashCode method.

We get the following code:

        @Override
        public @Nullable Packet getChildPacket() {
            return fChildPacket;
        }

        @Override
        public @Nullable ByteBuffer getPayload() {
            return fPayload;
        }

        /**
         * Getter method that returns the UDP Source Port.
         *
         * @return The source Port.
         */
        public int getSourcePort() {
            return fSourcePort;
        }

        /**
         * Getter method that returns the UDP Destination Port.
         *
         * @return The destination Port.
         */
        public int getDestinationPort() {
            return fDestinationPort;
        }

        /**
         * {@inheritDoc}
         *
         * See http://www.iana.org/assignments/service-names-port-numbers/service-
         * names-port-numbers.xhtml or
         * http://en.wikipedia.org/wiki/List_of_TCP_and_UDP_port_numbers
         */
        @Override
        protected @Nullable Packet findChildPacket() throws BadPacketException {
            // When more protocols are implemented, we can simply do a switch on the fDestinationPort field to find the child packet.
            // For instance, if the destination port is 80, then chances are the HTTP protocol is encapsulated. We can create a new HTTP
            // packet (after some verification that it is indeed the HTTP protocol).
            ByteBuffer payload = fPayload;
            if (payload == null) {
                return null;
            }

            return new UnknownPacket(getPcapFile(), this, payload);
        }

        @Override
        public boolean validate() {
            // Not yet implemented. ATM, we consider that all packets are valid.
            // TODO Implement it. We can compute the real checksum and compare it to fChecksum.
            return true;
        }

        @Override
        public UDPEndpoint getSourceEndpoint() {
            @Nullable
            UDPEndpoint endpoint = fSourceEndpoint;
            if (endpoint == null) {
                endpoint = new UDPEndpoint(this, true);
            }
            fSourceEndpoint = endpoint;
            return fSourceEndpoint;
        }

        @Override
        public UDPEndpoint getDestinationEndpoint() {
            @Nullable UDPEndpoint endpoint = fDestinationEndpoint;
            if (endpoint == null) {
                endpoint = new UDPEndpoint(this, false);
            }
            fDestinationEndpoint = endpoint;
            return fDestinationEndpoint;
        }

        @Override
        public Map<String, String> getFields() {
            ImmutableMap<String, String> map = fFields;
            if (map == null) {
                @SuppressWarnings("null")
                @NonNull ImmutableMap<String, String> newMap = ImmutableMap.<String, String> builder()
                        .put("Source Port", String.valueOf(fSourcePort)) //$NON-NLS-1$
                        .put("Destination Port", String.valueOf(fDestinationPort)) //$NON-NLS-1$
                        .put("Length", String.valueOf(fTotalLength) + " bytes") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("Checksum", String.format("%s%04x", "0x", fChecksum)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .build();
                fFields = newMap;
                return newMap;
            }
            return map;
        }

        @Override
        public String getLocalSummaryString() {
            return "Src Port: " + fSourcePort + ", Dst Port: " + fDestinationPort; //$NON-NLS-1$ //$NON-NLS-2$
        }

        @Override
        protected String getSignificationString() {
            return "Source Port: " + fSourcePort + ", Destination Port: " + fDestinationPort; //$NON-NLS-1$ //$NON-NLS-2$
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + fChecksum;
            final Packet child = fChildPacket;
            if (child != null) {
                result = prime * result + child.hashCode();
            } else {
                result = prime * result;
            }
            result = prime * result + fDestinationPort;
            final ByteBuffer payload = fPayload;
            if (payload != null) {
                result = prime * result + payload.hashCode();
            } else {
                result = prime * result;
            }
            result = prime * result + fSourcePort;
            result = prime * result + fTotalLength;
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UDPPacket other = (UDPPacket) obj;
            if (fChecksum != other.fChecksum) {
                return false;
            }
            final Packet child = fChildPacket;
            if (child != null) {
                if (!child.equals(other.fChildPacket)) {
                    return false;
                }
            } else {
                if (other.fChildPacket != null) {
                    return false;
                }
            }
            if (fDestinationPort != other.fDestinationPort) {
                return false;
            }
            final ByteBuffer payload = fPayload;
            if (payload != null) {
                if (!payload.equals(other.fPayload)) {
                    return false;
                }
            } else {
                if (other.fPayload != null) {
                    return false;
                }
            }
            if (fSourcePort != other.fSourcePort) {
                return false;
            }
            if (fTotalLength != other.fTotalLength) {
                return false;
            }
            return true;
        }

The UDPPacket class is implemented. We now have the define the
UDPEndpoint.

### Creating the UDPEndpoint

For the UDP protocol, an endpoint will be its source or its destination
port, depending if it is the source endpoint or destination endpoint.
Knowing that, we can create our UDPEndpoint class.

We create in our package a new class named UDPEndpoint that extends
ProtocolEndpoint. We also add a field: fPort, which contains the source
or destination port. We finally add a constructor public
ExampleEndpoint(Packet packet, boolean isSourceEndpoint):

- *Packet* **packet**: the packet to build the endpoint from.
- *boolean* **isSourceEndpoint**: whether the endpoint is the source
  endpoint or destination endpoint.

We obtain the following unimplemented class:

    package org.eclipse.tracecompass.pcap.core.protocol.udp;

    import org.eclipse.tracecompass.internal.pcap.core.endpoint.ProtocolEndpoint;
    import org.eclipse.tracecompass.internal.pcap.core.packet.Packet;

    public class UDPEndpoint extends ProtocolEndpoint {

        private final int fPort;

        public UDPEndpoint(Packet packet, boolean isSourceEndpoint) {
            super(packet, isSourceEndpoint);
            // TODO Auto-generated constructor stub
        }

        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public String toString() {
            // TODO Auto-generated method stub
            return null;
        }

    }

For the constructor, we simply initialize fPort. If isSourceEndpoint is
true, then we take packet.getSourcePort(), else we take
packet.getDestinationPort().

        /**
         * Constructor of the {@link UDPEndpoint} class. It takes a packet to get
         * its endpoint. Since every packet has two endpoints (source and
         * destination), the isSourceEndpoint parameter is used to specify which
         * endpoint to take.
         *
         * @param packet
         *            The packet that contains the endpoints.
         * @param isSourceEndpoint
         *            Whether to take the source or the destination endpoint of the
         *            packet.
         */
        public UDPEndpoint(UDPPacket packet, boolean isSourceEndpoint) {
            super(packet, isSourceEndpoint);
            fPort = isSourceEndpoint ? packet.getSourcePort() : packet.getDestinationPort();
        }

Then we implement the methods:

- *public int* **hashCode()**: method that returns an integer based on
  the fields value. In our case, it will return an integer depending on
  fPort, and the parent endpoint that we can retrieve with
  getParentEndpoint().
- *public boolean* **equals(Object obj)**: method that returns true if
  two objects are equals. In our case, two UDPEndpoints are equal if
  they both have the same fPort and have the same parent endpoint that
  we can retrieve with getParentEndpoint().
- *public String* **toString()**: method that returns a description of
  the UDPEndpoint as a string. In our case, it will be a concatenation
  of the string of the parent endpoint and fPort as a string.

<!-- -->

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            ProtocolEndpoint endpoint = getParentEndpoint();
            if (endpoint == null) {
                result = 0;
            } else {
                result = endpoint.hashCode();
            }
            result = prime * result + fPort;
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UDPEndpoint)) {
                return false;
            }

            UDPEndpoint other = (UDPEndpoint) obj;

            // Check on layer
            boolean localEquals = (fPort == other.fPort);
            if (!localEquals) {
                return false;
            }

            // Check above layers.
            ProtocolEndpoint endpoint = getParentEndpoint();
            if (endpoint != null) {
                return endpoint.equals(other.getParentEndpoint());
            }
            return true;
        }

        @Override
        public String toString() {
            ProtocolEndpoint endpoint = getParentEndpoint();
            if (endpoint == null) {
                @SuppressWarnings("null")
                @NonNull String ret = String.valueOf(fPort);
                return ret;
            }
            return endpoint.toString() + '/' + fPort;
        }

### Registering the UDP protocol

The last step is to register the new protocol. There are three places
where the protocol has to be registered. First, the parser has to know
that a new protocol has been added. This is defined in the enum
org.eclipse.tracecompass.internal.pcap.core.protocol.PcapProtocol.
Simply add the protocol name here, along with a few arguments:

- *String* **longname**, which is the long version of name of the
  protocol. In our case, it is "User Datagram Protocol".
- *String* **shortName**, which is the shortened name of the protocol.
  In our case, it is "UDP".
- *Layer* **layer**, which is the layer to which the protocol belongs in
  the OSI model. In our case, this is the layer 4.
- *boolean* **supportsStream**, which defines whether or not the
  protocol supports packet streams. In our case, this is set to true.

Thus, the following line is added in the PcapProtocol enum:

        UDP("User Datagram Protocol", "udp", Layer.LAYER_4, true),

Also, TMF has to know about the new protocol. This is defined in
org.eclipse.tracecompass.internal.tmf.pcap.core.protocol.TmfPcapProtocol.
We simply add it, with a reference to the corresponding protocol in
PcapProtocol. Thus, the following line is added in the TmfPcapProtocol
enum:

        UDP(PcapProtocol.UDP),

You will also have to update the *ProtocolConversion* class to register
the protocol in the switch statements. Thus, for UDP, we add:

        case UDP:
            return TmfPcapProtocol.UDP;

and

        case UDP:
            return PcapProtocol.UDP;

Finally, all the protocols that could be the parent of the new protocol
(in our case, IPv4 and IPv6) have to be notified of the new protocol.
This is done by modifying the findChildPacket() method of the packet
class of those protocols. For instance, in IPv4Packet, we add a case in
the switch statement of findChildPacket, if the Protocol number matches
UDP's protocol number at the network layer:

        @Override
        protected @Nullable Packet findChildPacket() throws BadPacketException {
            ByteBuffer payload = fPayload;
            if (payload == null) {
                return null;
            }

            switch (fIpDatagramProtocol) {
            case IPProtocolNumberHelper.PROTOCOL_NUMBER_TCP:
                return new TCPPacket(getPcapFile(), this, payload);
            case IPProtocolNumberHelper.PROTOCOL_NUMBER_UDP:
                return new UDPPacket(getPcapFile(), this, payload);
            default:
                return new UnknownPacket(getPcapFile(), this, payload);
            }
        }

The new protocol has been added. Running TMF should work just fine, and
the new protocol is now recognized.

## Adding stream-based views

To add a stream-based View, simply monitor the
TmfPacketStreamSelectedSignal in your view. It contains the new stream
that you can retrieve with signal.getStream(). You must then make an
event request to the current trace to get the events, and use the stream
to filter the events of interest. Therefore, you must also monitor
TmfTraceOpenedSignal, TmfTraceClosedSignal and TmfTraceSelectedSignal.
Examples of stream-based views include a view that represents the
packets as a sequence diagram, or that shows the TCP connection state
based on the packets SYN/ACK/FIN/RST flags. A (very very very early)
draft of such a view can be found at
<https://git.eclipse.org/r/#/c/31054/>.

## TODO

- Add more protocols. At the moment, only four protocols are supported.
  The following protocols would need to be implemented: ARP, SLL, WLAN,
  USB, IPv6, ICMP, ICMPv6, IGMP, IGMPv6, SCTP, DNS, FTP, HTTP, RTP, SIP,
  SSH and Telnet. Other VoIP protocols would be nice.
- Add a network graph view. It would be useful to produce graphs that
  are meaningful to network engineers, and that they could use (for
  presentation purpose, for instance). We could use the XML-based
  analysis to do that!
- Add a Stream Diagram view. This view would represent a stream as a
  Sequence Diagram. It would be updated when a TmfNewPacketStreamSignal
  is thrown. It would be easy to see the packet exchange and the time
  delta between each packet. Also, when a packet is selected in the
  Stream Diagram, it should be selected in the event table and its
  content should be shown in the Properties View. See
  <https://git.eclipse.org/r/#/c/31054/> for a draft of such a view.
- Make adding protocol more "plugin-ish", via extension points for
  instance. This would make it easier to support new protocols, without
  modifying the source code.
- Control dumpcap directly from eclipse, similar to how LTTng is
  controlled in the Control View.
- Support pcapng. See:
  <http://www.winpcap.org/ntar/draft/PCAP-DumpFileFormat.html> for the
  file format.
- Add SWTBOT tests to org.eclipse.tracecompass.tmf.pcap.ui
- Add a Raw Viewer, similar to Wireshark. We could use the \ufffdShow Raw\ufffd in
  the event editor to do that.
- Externalize strings in org.eclipse.tracecompass.pcap.core. At the
  moment, all the strings are hardcoded. It would be good to externalize
  them all.
