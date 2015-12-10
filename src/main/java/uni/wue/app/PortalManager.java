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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
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
    private Map<Host, Host> primaryDst;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");
        primaryDst = new HashMap<Host, Host>();

        packetService.addProcessor(processor, PacketProcessor.director(3));

        log.info("Started PortalManager");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        primaryDst = null;
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
            if(portal != null && ethPkt.getEtherType() == Ethernet.TYPE_IPV4){

                // filter out packets from or to the portal
                if(ethPkt.getSourceMAC().equals(portal.mac())){
                    // get the destination host
                    Set<Host> dst = hostService.getHostsByMac(ethPkt.getDestinationMAC());
                    if(dst.iterator().hasNext()){
                        Host dstHost = dst.iterator().next();
                        // check if the packet of the dstHost was changed before
                        if(primaryDst.containsKey(dstHost)){
                            log.debug(byodMarker, "Restore the source of the previous intended packet source");
                            restoreSource(context, primaryDst.get(dstHost));
                            return;
                        }
                    }
                    return;
                } else if(ethPkt.getDestinationMAC().equals(portal.mac())){
                    log.debug(byodMarker, "Do not change the packets with the portal as destination.");
                    return;
                } else {
                    // save the destination and source pairs to restore it later
                    Set<Host> src = hostService.getHostsByMac(ethPkt.getSourceMAC());
                    Set<Host> dst = hostService.getHostsByMac(ethPkt.getDestinationMAC());
                    if(src.iterator().hasNext() && dst.iterator().hasNext())
                        primaryDst.put(src.iterator().next(), dst.iterator().next());

                    //send the packet to destination port of portal
                    sendToPortal(context);

                    return;
                }
            }
            return;
        }
    }

    /**
     * Restores the packet source from the portal to the previous intended source
     * @param context the packet context
     * @param previousSrc previously intended packet destination
     */
    private void restoreSource(PacketContext context, Host previousSrc){
        checkNotNull(context, "No context defined!");
        checkNotNull(previousSrc, "No previous host defined!");
        checkNotNull(previousSrc.ipAddresses().iterator().next(), "No ip address for previous source defined!");

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        Host dstHost;
        // get the destination host
        Set<Host> dst = hostService.getHostsByMac(ethPkt.getDestinationMAC());
        if(dst.iterator().hasNext())
            dstHost = dst.iterator().next();
        else
            throw new NullPointerException(String.format("No dst host found with mac address %s",
                    ethPkt.getDestinationMAC()));

        // restore the mac address
        ethPkt.setSourceMACAddress(previousSrc.mac());
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();
        // restore the ip address
        ipPkt.setSourceAddress(previousSrc.ipAddresses().iterator().next().getIp4Address().toInt());
        ipPkt.resetChecksum();
        // wrap the packet
        ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());

        // set up the traffic management
        builder.setIpSrc(previousSrc.ipAddresses().iterator().next())
                .setEthSrc(previousSrc.mac());

        // packet send from the switch with the destination host connected to
        if(pkt.receivedFrom().deviceId().equals(dstHost.location().deviceId())){
            if(!pkt.receivedFrom().port().equals(dstHost.location().port())){
                builder.setOutput(dstHost.location().port());
                packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), builder.build(), buf));
                log.debug(byodMarker, "Send packet to port " + dstHost.location().port().toString());
            }
            return;
        }

        Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                pkt.receivedFrom().deviceId(),
                dstHost.location().deviceId());
        if(paths.isEmpty()){
            return;
        }

        Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());
        if(path == null){
            Object[] pathDetails = {pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC()};
            log.warn("Don't know where to go from here {} for {} -> {}", pathDetails);
            //flood(context);
            return;
        }

        // Otherwise forward and be done with it.
        builder.setOutput(path.src().port());
        packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), builder.build(), buf));
        log.debug(byodMarker, "Send packet to port " + dstHost.location().port().toString());
    }

    // Sends a packet out to its destination.
    private void sendToPortal(PacketContext context) {

        checkNotNull(portal, "No portal defined!");
        checkNotNull(portal.ipAddresses().iterator().next(), "Portal has no IP-address!");

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        //set the mac address of the portal as destination
        ethPkt.setDestinationMACAddress(portal.mac());
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();
        // set the ip address of the portal as destination
        ipPkt.setDestinationAddress((portal.ipAddresses().iterator().next().getIp4Address()).toInt());
        ipPkt.resetChecksum();
        //wrap the packet as buffer
        ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());

        // set up the traffic management
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

    private void changeSource(PacketContext contex, Host newSource){


    }

    @Override
    public void setPortal(String portalId) {
        checkNotNull(portalId, "Portal-ID can not be null");

        portal = hostService.getHost(HostId.hostId(portalId));
        checkNotNull(portal, String.format("No host with host-id %s found", portalId));

        System.out.println(String.format("Set portal to %s", portal.id().toString()));
    }

    /**
     * Get the Mac Address of the portal
     *
     * @return MacAddress
     */
    @Override
    public MacAddress getPortalMac() {
        return new MacAddress(portal.mac().toBytes());
    }

    /**
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    @Override
    public Set<IpAddress> getPortalIp() { return portal.ipAddresses(); }

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
