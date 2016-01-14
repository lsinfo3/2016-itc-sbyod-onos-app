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

import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
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
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 11.12.15.
 */
@Component(immediate = true, inherit = true)
@Service
public class FlowRedirect extends PacketRedirect {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected CoreService coreService;
//
//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected HostService hostService;

    protected Host portal;
    private final Logger log = LoggerFactory.getLogger(getClass());
    protected ApplicationId appId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app.flowRedirect");
        log.info("Started FlowRedirect");
    }

    @Deactivate
    protected void deactivate() {
        removeFlows();
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

        this.portal = portal;
        flowToPortal(context);
        flowFromPortal(context);
        sendPacket(context);
        return;
    }

    /**
     * Restore the actual source of the packet
     *
     * @param context
     */
    @Override
    public void restoreSource(PacketContext context) {
        // do nothing as we already added the corresponding flow rules
        return;
    }


    /**
     * Add flow table to redirect the packets to the portal
     * @param context the packet context
     */
    private void flowToPortal(PacketContext context){
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
                .forTable(0)
                .forDevice(context.inPacket().receivedFrom().deviceId());

        FlowRule fr = flowBuilder.build();
        flowRuleService.applyFlowRules(fr);
        waitForRuleEntry(fr, pkt.receivedFrom().deviceId());

        return;
    }

    /**
     * Add flow table to redirect the packets coming from the portal
     * @param context the packet context
     */
    private void flowFromPortal(PacketContext context){
        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

        // get destination host
        // use source of the packet for destination, as we are applying this
        // rule immediately when redirecting the packet to the portal
        Host dstHost;
        Set<Host> hosts = hostService.getHostsByMac(ethPkt.getSourceMAC());
        if(hosts.iterator().hasNext())
            dstHost = hosts.iterator().next();
        else{
            log.warn(byodMarker, String.format("No host found with mac %s", ethPkt.getSourceMAC().toString()));
            return;
        }

        // traffic selector for packets of type IPv4
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                .matchEthSrc(portal.mac())
                .matchEthDst(ethPkt.getSourceMAC());

        // get the port where the packet from the portal should come from
        PortNumber fromPort = getDstPort(pkt, portal);
        // get the port where the packet should be send to
        PortNumber outPort = getDstPort(pkt, dstHost, fromPort);
        if(outPort == null) {
            log.warn(byodMarker, String.format("Could not find a path from the portal to dstHost %s.",
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

        FlowRule fr = flowBuilder.build();
        flowRuleService.applyFlowRules(fr);
        waitForRuleEntry(fr, pkt.receivedFrom().deviceId());
        return;
    }

    /**
     * Ensures that the flowRule has been added to the device before anything else is done
     * @param flowRule to be added
     * @param deviceId of flow entry
     */
    private void waitForRuleEntry(FlowRule flowRule, DeviceId deviceId){
        while(true) {
            Iterable<FlowEntry> flowEntries = flowRuleService.getFlowEntries(deviceId);
            for(FlowEntry fe : flowEntries){
                if(flowRule.id().equals(fe.id())){
                    return;
                }
            }
        }
    }

    /**
     * Send out the handled packet otherwise it would be blocked
     * @param context packet context to handle
     */
    private void sendPacket(PacketContext context) {

        /* TODO: more easy than copying packet?
        context.treatmentBuilder().setOutput(PortNumber.TABLE);
        context.send();
        */

        //parse the packet of the context
        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        IPv4 ipPkt = (IPv4)ethPkt.getPayload();

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

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder();
        // set up the traffic management
        trafficTreatmentBuilder.setIpDst(portal.ipAddresses().iterator().next())
                .setEthDst(portal.mac())
                .setOutput(outPort);

        //send new packet to the device where received packet came from
        packetService.emit(new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), trafficTreatmentBuilder.build(), buf));
        context.block();
        return;
    }

    /**
     * Removes the flows created by this application
     */
    public void removeFlows(){
        Iterable<FlowRule> flows = flowRuleService.getFlowRulesById(appId);
        for(FlowRule flow : flows){
            flowRuleService.removeFlowRules(flow);
        }
    }
}
