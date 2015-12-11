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
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 11.12.15.
 */
@Component(immediate = true)
@Service
public class FlowRedirect implements PacketRedirectService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    private final Logger log = LoggerFactory.getLogger(getClass());
    Marker byodMarker = MarkerFactory.getMarker("BYOD");
    protected ApplicationId appId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app.flowRedirect");
        log.info("Started FlowRedirect");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped FlowRedirect");
    }

    /**
     * Change the destination to match with portal
     *
     * @param context
     */
    @Override
    public void redirectToPortal(PacketContext context, Host portal) {
        checkNotNull(context, "No context defined!");
        checkNotNull(portal, "No portal defined!");

        flowToPortal(context, portal);
        flowFromPortal(context, portal);
        return;
    }

    /**
     * Restore the actual source of the packet
     *
     * @param context
     */
    @Override
    public void restoreSource(PacketContext context) {
        return;
    }

    private void flowToPortal(PacketContext context, Host portal){
        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();

        // traffic selector for packets of type IPv4
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchEthSrc(ethPkt.getSourceMAC())
                .matchEthDst(ethPkt.getDestinationMAC());

        PortNumber outPort = getDstPort(pkt, portal);
        if(outPort == null) {
            log.warn(byodMarker, String.format("Could not find a path from %s to the portal.",
                    pkt.receivedFrom().deviceId().toString()));
            return;
        }
        // change the destination to portal mac and ip address
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        treatmentBuilder.setIpDst(portal.ipAddresses().iterator().next())
                .setEthDst(portal.mac())
                .setOutput(outPort);

        // create flow rule
        FlowRule.Builder flowBuilder = DefaultFlowRule.builder();
        flowBuilder.withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .fromApp(appId)
                .makeTemporary(120)
                .withPriority(1000)
                .forDevice(context.inPacket().receivedFrom().deviceId());

        flowRuleService.applyFlowRules(flowBuilder.build());
    }

    private void flowFromPortal(PacketContext context, Host portal){
        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

        // get destination host
        Host dstHost;
        Set<Host> hosts = hostService.getHostsByMac(ethPkt.getDestinationMAC());
        if(hosts.iterator().hasNext())
            dstHost = hosts.iterator().next();
        else{
            log.warn(byodMarker, String.format("No host found with mac %s", ethPkt.getDestinationMAC().toString()));
            return;
        }

        // traffic selector for packets of type IPv4
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchEthSrc(portal.mac())
                .matchEthDst(ethPkt.getDestinationMAC());

        PortNumber outPort = getDstPort(pkt, dstHost);
        if(outPort == null) {
            log.warn(byodMarker, String.format("Could not find a path from %s to the portal.",
                    pkt.receivedFrom().deviceId().toString()));
            return;
        }

        // change the destination to portal mac and ip address
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        treatmentBuilder.setEthSrc(ethPkt.getDestinationMAC())
                .setIpSrc(IpAddress.valueOf(ipPkt.getDestinationAddress()))
                .setOutput(outPort);

        // create flow rule
        FlowRule.Builder flowBuilder = DefaultFlowRule.builder();
        flowBuilder.withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .fromApp(appId)
                .makeTemporary(120)
                .withPriority(1000)
                .forDevice(context.inPacket().receivedFrom().deviceId());

        flowRuleService.applyFlowRules(flowBuilder.build());
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
