/*
 * Copyright 2015 Lorenz Reinhart
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package uni.wue.app;

import jdk.nashorn.internal.objects.Global;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.packet.*;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;


/**
 * Created by lorry on 13.11.15.
 */
@Component(immediate = true)
@Service
public class PortalManager implements PortalService{

    private final Logger log = LoggerFactory.getLogger(getClass());
    Marker byodMarker = MarkerFactory.getMarker("BYOD");

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    protected ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    private Host portal;
    private MacAddress portalMac;
    private IpAddress portalIp;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");

        packetService.addProcessor(processor, PacketProcessor.director(3));

        log.info("Started PortalManager");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            //parse the packet of the context
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }

            //print out the ethernet type of the packet
            //EthType type = new EthType(ethPkt.getEtherType());
            //log.debug(byodMarker, "Packet with ethType: " + type.toString());

            //if portal is defined -> change destination of packet with ipv4 type
            if(portalMac != null && ethPkt.getEtherType() == Ethernet.TYPE_IPV4){

                //do not change packets from or to portal device
                if(ethPkt.getSourceMAC().equals(portalMac) || ethPkt.getDestinationMAC().equals(portalMac)){
                    log.debug(byodMarker, "Did not change mac address of source "
                            + (ethPkt.getSourceMAC()).toString());
                    return;
                } else {
                    MacAddress oldMac = ethPkt.getDestinationMAC();
                    //log.debug(byodMarker, "Changed packet destination mac address from " + oldMac.toString()
                    //        + " to " + portalMac.toString());

                    //send the packet to destination port of portal
                    packetOut(context);

                    return;
                }
            }
            return;
        }
    }

    // Sends a packet out to its destination.
    private void packetOut(PacketContext context) {

        checkNotNull(portal);
        checkNotNull(portal.ipAddresses().iterator().next());

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();;
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        //set destination mac and ip of portal
        ethPkt.setDestinationMACAddress(portal.mac());
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

        ipPkt.setDestinationAddress((portal.ipAddresses().iterator().next().getIp4Address()).toInt());
        //wrap the packet as buffer
        ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());

        // Do we know the portal host? If not, flood and bail.
        if (portal == null) {
            //flood(context);
            return;
        }

        //all packets go to portal
        if(portal.ipAddresses().iterator().hasNext())
            builder.setIpDst(portal.ipAddresses().iterator().next())
                .setEthDst(portal.mac());

        // Are we on an edge switch that our portal is on? If so,
        // simply forward out to the destination and bail.
        if (pkt.receivedFrom().deviceId().equals(portal.location().deviceId())) {
            // if the packet is not send from the same port as the portal
            if (!context.inPacket().receivedFrom().port().equals(portal.location().port())) {
                builder.setOutput(portal.location().port());
                //send new packet to the device where received packet came from
                packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), builder.build(), buf));
                log.debug(byodMarker, "Send context to port " + portal.location().port().toString());
            }
            return;
        }

        // Otherwise, get a set of paths that lead from here to the
        // destination edge switch.
        Set<Path> paths =
                topologyService.getPaths(topologyService.currentTopology(),
                        pkt.receivedFrom().deviceId(),
                        portal.location().deviceId());
        if (paths.isEmpty()) {
            // If there are no paths, flood and bail.
            //flood(context);
            return;
        }

        // Otherwise, pick a path that does not lead back to where we
        // came from; if no such path, flood and bail.
        Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());
        if (path == null) {
            Object[] pathDetails = {pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC()};
            log.warn("Don't know where to go from here {} for {} -> {}", pathDetails);
            //flood(context);
            return;
        }

        // Otherwise forward and be done with it.
        builder.setOutput(path.src().port());
        packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), builder.build(), buf));

    }

    // Selects a path from the given set that does not lead back to the
    // specified port if possible.
    private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
        Path lastPath = null;
        for (Path path : paths) {
            lastPath = path;
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return lastPath;
    }

    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    @Override
    public void setPortal(String portalId) {
        checkNotNull(portalId, "Portal-ID can not be null");

        portal = hostService.getHost(HostId.hostId(portalId));
        checkNotNull(portal, String.format("No host with host-id %s found", portalId));

        portalMac = portal.mac();
        portalIp = portal.ipAddresses().iterator().next();

        System.out.println(String.format("Set portal to %s", portal.id().toString()));
    }

    /**
     * Get the Mac Address of the portal
     *
     * @return MacAddress
     */
    @Override
    public MacAddress getPortalMac() {
        return new MacAddress(portalMac.toBytes());
    }

    /**
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    @Override
    public Set<IpAddress> getPortalIp() {
        return portal.ipAddresses();
    }

    /**
     * Get the portal as host
     *
     * @return Host
     */
    @Override
    public Host getPortal() {
        return portal;
    }

}
