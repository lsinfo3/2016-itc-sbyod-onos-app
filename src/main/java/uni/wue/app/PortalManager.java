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
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.packet.*;
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
    protected FlowRuleService flowRuleService;

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
    // source and destination pairs of changed packets
    private Map<Host, Host> primaryDst;
    private PacketRedirectService packetRedirectService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");
        primaryDst = new HashMap<Host, Host>();
        packetRedirectService = DefaultServiceDirectory.getService(PacketRedirectService.class);

        packetService.addProcessor(processor, PacketProcessor.director(3));

        log.info("Started PortalManager");
    }

    @Deactivate
    protected void deactivate() {
        removeFlows();
        packetService.removeProcessor(processor);
        primaryDst = null;
        packetRedirectService = null;
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

            //if portal is defined -> change destination of packet with ipv4 type
            if(portal != null && ethPkt.getEtherType() == Ethernet.TYPE_IPV4){

                // filter out packets from the portal
                if(ethPkt.getSourceMAC().equals(portal.mac())){
                    packetRedirectService.restoreSource(context);
                    // TODO: rule is not applied, as all IPv4 packets are handled by the sendToPortalFlow!
                    // TODO: add rule that pushes all unknown portal packets to the controller
                    //preventPortalFlow(context);
                    return;
                } else if(ethPkt.getDestinationMAC().equals(portal.mac())){
                    log.debug(byodMarker, "Do not change the packets with the portal as destination.");
                    return;
                } else {
                    packetRedirectService.redirectToPortal(context, portal);
                    return;
                }
            }
            return;
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
     * Add flow table to redirect the packets to the portal
     * @param context the packet context
     */
    private void sendToPortalFlow(PacketContext context){

        InboundPacket pkt = context.inPacket();

        // traffic selector for packets of type IPv4
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);

        PortNumber outPort = getDstPort(pkt, portal);
        if(outPort == null) {
            log.warn(byodMarker, String.format("Could not find a path from %s to the portal.",
                    pkt.receivedFrom().deviceId().toString()));
            return;
        }

        // change the destination to portal mac and ip address
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        treatmentBuilder.setIpDst(this.getPortalIp().iterator().next())
                .setEthDst(this.getPortalMac())
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

    /**
     * Prevent the packets coming from the portal to be changed
     * @param context the packet context
     */
    private void preventPortalFlow(PacketContext context){

        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        Host dstHost;
        // get destination host
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
                .matchEthSrc(this.getPortalMac())
                .matchIPSrc(this.getPortalIp().iterator().next().toIpPrefix());

        PortNumber outPort = getDstPort(pkt, dstHost);
        if(outPort == null) {
            log.warn(byodMarker, String.format("Could not find a path from %s to the portal.",
                    pkt.receivedFrom().deviceId().toString()));
            return;
        }

        // change the destination to portal mac and ip address
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        treatmentBuilder.setOutput(outPort);

        // create flow rule
        FlowRule.Builder flowBuilder = DefaultFlowRule.builder();
        flowBuilder.withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .fromApp(appId)
                .makeTemporary(120)
                .withPriority(1010)
                .forDevice(context.inPacket().receivedFrom().deviceId());

        flowRuleService.applyFlowRules(flowBuilder.build());

    }

    /**
     * Removes the flows created by this application
     */
    private void removeFlows(){
        Iterable<FlowRule> flows = flowRuleService.getFlowRulesById(appId);
        for(FlowRule flow : flows){
            flowRuleService.removeFlowRules(flow);
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

    /**
     * Indicates whether this is a control packet, e.g. LLDP, BDDP
     * @param eth Ethernet packet
     * @return true if eth is a control packet
     */
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
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
