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
package uni.wue.app.redirect;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
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
import java.util.Set;

/**
 * Created by lorry on 11.12.15.
 */
@Component(componentAbstract = true)
public abstract class PacketRedirect implements PacketRedirectService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    private final Logger log = LoggerFactory.getLogger(getClass());
    protected Marker byodMarker = MarkerFactory.getMarker("BYOD");


    /**
     * Change the destination to match with portal
     * @param context
     */
    public abstract void redirectToPortal(PacketContext context, Host portal);

    /**
     * Restore the actual source of the packet
     * @param context
     * @param portal
     */
    public abstract void restoreSource(PacketContext context, Host portal);

    protected PortNumber getDstPort(InboundPacket pkt, Host dstHost){
        return getDstPort(pkt, dstHost, pkt.receivedFrom().port());
    }

    protected PortNumber getDstPort(InboundPacket pkt, Host dstHost, PortNumber srcPortNr){

        // Are we on an edge switch that our dstHost is on? If so,
        // simply forward out to the destination.
        if (pkt.receivedFrom().deviceId().equals(dstHost.location().deviceId())) {
            // if the packet is not send from the same port as the portal
            if (!srcPortNr.equals(dstHost.location().port())) {
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
            Path path = pickForwardPathIfPossible(paths, srcPortNr);
            if (path == null) {
                Object[] pathDetails = {pkt.receivedFrom(), dstHost.id()};
                log.warn("Don't know where to go from here {} to {}", pathDetails);
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
    protected Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
        Path lastPath = null;
        for (Path path : paths) {
            lastPath = path;
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return lastPath;
    }

    public void FloodPacket(PacketContext context, Ip4Address hostIp){
        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

        //set the mac address of the portal as destination
        ethPkt.setDestinationMACAddress(MacAddress.BROADCAST);
        // set the ip address of the portal as destination
        ipPkt.setDestinationAddress(hostIp.toInt());
        ipPkt.resetChecksum();
        //wrap the packet as buffer
        ByteBuffer buf = ByteBuffer.wrap(ethPkt.serialize());

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder();
        // set up the traffic management
        trafficTreatmentBuilder.setIpDst(hostIp)
                .setOutput(PortNumber.FLOOD);

        //send new packet to the device where received packet came from
        // TODO: failure when sending the packet out!
        packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), trafficTreatmentBuilder.build(), buf));
        context.isHandled();
        context.block();
        return;
    }
}
