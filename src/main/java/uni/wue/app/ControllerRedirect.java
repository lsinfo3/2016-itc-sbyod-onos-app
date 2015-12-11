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

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 11.12.15.
 */
@Component(immediate = false)
public class ControllerRedirect implements PacketRedirectService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    private final Logger log = LoggerFactory.getLogger(getClass());
    Marker byodMarker = MarkerFactory.getMarker("BYOD");

    protected ApplicationId appId;

    // source and destination pairs of changed packets
    private Map<Host, Host> primaryDst;

    public ControllerRedirect() {
        this.primaryDst = new HashMap<>();
    }

    @Activate
    protected void activate(){
        appId = coreService.registerApplication("uni.wue.app.controllerRedirect");
        log.info("Started ControllerRedirect");
    }

    @Deactivate
    protected void deactivate(){
        log.info("Stopped ControllerRedirect");
    }

    /**
     * Create new packet and send it to the portal
     * @param context the packet context
     * @param portal the portal all packets are send to
     */
    @Override
    public void redirectToPortal(PacketContext context, Host portal) {
        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

        // save the destination and source pairs to restore it later
        Set<Host> src = hostService.getHostsByMac(ethPkt.getSourceMAC());
        Set<Host> dst = hostService.getHostsByMac(ethPkt.getDestinationMAC());
        if(src.iterator().hasNext() && dst.iterator().hasNext())
            primaryDst.put(src.iterator().next(), dst.iterator().next());

        checkNotNull(portal, "No portal defined!");
        checkNotNull(portal.ipAddresses().iterator().next(), "Portal has no IP-address!");

        //set the mac address of the portal as destination
        ethPkt.setDestinationMACAddress(portal.mac());
        // set the ip address of the portal as destination
        ipPkt.setDestinationAddress((portal.ipAddresses().iterator().next().getIp4Address()).toInt());
        ipPkt.resetChecksum();
        //wrap the packet as buffer
        ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());

        PortNumber outPort = getDstPort(pkt, portal);
        if(outPort == null) {
            log.warn(byodMarker, String.format("Could not find a path from %s to the portal.",
                    pkt.receivedFrom().deviceId().toString()));
            return;
        }
        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        // set up the traffic management
        builder.setIpDst(portal.ipAddresses().iterator().next())
                .setEthDst(portal.mac())
                .setOutput(outPort);

        //send new packet to the device where received packet came from
        packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), builder.build(), buf));
        context.block();
        return;
    }

    /**
     * Restores the packet source from the portal to the previous intended source
     * and send a copy of the packet
     *
     * @param context the packet context
     */
    @Override
    public void restoreSource(PacketContext context) {
        checkNotNull(context, "No context defined!");
        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

        // get the destination host
        Set<Host> dst = hostService.getHostsByMac(ethPkt.getDestinationMAC());
        if(dst.iterator().hasNext()) {
            Host dstHost = dst.iterator().next();
            // check if the packet of the dstHost was changed before
            if(primaryDst.containsKey(dstHost)) {
                Host previousSrc = primaryDst.get(dstHost);
                if(!previousSrc.ipAddresses().iterator().hasNext())
                    log.warn(byodMarker, String.format("No ip address for previous source %s defined!",
                            previousSrc.id().toString()));
                else {

                    log.debug(byodMarker, "Restore the source of the previous intended packet source");
                    // restore the mac address
                    ethPkt.setSourceMACAddress(previousSrc.mac());
                    // restore the ip address
                    ipPkt.setSourceAddress(previousSrc.ipAddresses().iterator().next().getIp4Address().toInt());
                    ipPkt.resetChecksum();
                    // wrap the packet
                    ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());

                    PortNumber outPort = getDstPort(pkt, dstHost);
                    if(outPort == null) {
                        log.warn(byodMarker, String.format("Could not find a path from %s to %s.",
                                pkt.receivedFrom().deviceId().toString()), dstHost.id().toString());
                        return;
                    }
                    TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
                    // set up the traffic management
                    builder.setIpSrc(previousSrc.ipAddresses().iterator().next())
                            .setEthSrc(previousSrc.mac())
                            .setOutput(outPort);

                    //send new packet to the device where received packet came from
                    packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), builder.build(), buf));
                    context.block();
                    return;
                }
            }
        }
    }

    private PortNumber getDstPort(InboundPacket pkt, Host dstHost){

        Ethernet ethPkt = pkt.parsed();

        // Are we on an edge switch that our dstHost is on? If so,
        // simply forward out to the destination.
        if (pkt.receivedFrom().deviceId().equals(dstHost.location().deviceId())) {
            // if the packet is not send from the same port as the portal
            if (!pkt.receivedFrom().port().equals(dstHost.location().port())) {
                //return the actual port of the portal
                return dstHost.location().port();
            } else
                return null;
        } else {
            // Otherwise get a set of paths that lead from here to the
            // destination edge switch.
            Set<Path> paths =
                    topologyService.getPaths(topologyService.currentTopology(),
                            pkt.receivedFrom().deviceId(),
                            dstHost.location().deviceId());
            if (paths.isEmpty()) {
                // If there are no paths
                return null;
            }

            // pick a path that does not lead back to where we came from
            Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());
            if (path == null) {
                Object[] pathDetails = {pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC()};
                log.warn("Don't know where to go from here {} for {} -> {}", pathDetails);
                // if no such path exists return null
                return null;
            } else {
                // return the first port of the path
                return path.src().port();
            }
        }
    }

    /**
     * Selects a path from the given set that does not lead back to the
     * specified port if possible.
     * @param paths a set of paths
     * @param notToPort source port is different from this port
     * @return a path with source different from notToPort
     */
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
}
