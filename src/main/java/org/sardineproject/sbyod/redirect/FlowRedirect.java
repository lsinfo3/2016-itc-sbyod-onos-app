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
package org.sardineproject.sbyod.redirect;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.sardineproject.sbyod.PortalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 11.12.15.
 */
//@Component(immediate = true, inherit = true)
//@Service
public class FlowRedirect extends PacketRedirect {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final int TIMEOUT = 3; //seconds
    private static final int RULE_PRIORITY = 400;
    // defines the table number
    private static final int FLOW_TABLE = 100;
    // does the switch packet selector support ethernet mac matching
    private static final boolean MATCH_ETH_DST = false;


    private final Lock lock = new ReentrantLock();
    private final Condition ruleAdded = lock.newCondition();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    protected Host portal;
    private final Logger log = LoggerFactory.getLogger(getClass());
    protected ApplicationId appId;


    @Activate
    protected void activate() {
        appId = applicationIdStore.getAppId(APPLICATION_ID);
    }

    @Deactivate
    protected void deactivate() {
    }

    /**
     * Redirected the context to the portal
     *
     * @param context of the packet
     */
    @Override
    public void redirectToPortal(PacketContext context, Host portal) {
        checkNotNull(context, "No context defined!");
        checkNotNull(portal, "No portal defined!");

        this.portal = portal;
        flowToPortal(context);
        flowFromPortal(context);
    }

    /**
     * Restore the actual source of the context
     *
     * @param context of the packet
     * @param portal redirecting to
     */
    @Override
    public void restoreSource(PacketContext context, Host portal) {
        // as there seems to be no rule installed yet, do it now
        redirectToPortal(context, portal);
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
                .matchEthSrc(ethPkt.getSourceMAC());
        if(MATCH_ETH_DST) {
            selectorBuilder.matchEthDst(ethPkt.getDestinationMAC());
        }

        // no port is found as long as the portal is not present to the controller
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
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .withPriority(RULE_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();

        log.debug("FlowRedirect: Adding flow objective \n{} \nfor device {} in method flowToPortal():\n{}",
                forwardingObjective, context.inPacket().receivedFrom().deviceId());
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);
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
                .matchEthSrc(portal.mac());
        if(MATCH_ETH_DST) {
            selectorBuilder.matchEthDst(ethPkt.getSourceMAC());
        }

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
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .withPriority(RULE_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();

        log.debug("FlowRedirect: Adding flow objective \n{} \nfor device {} in method flowFromPortal():\n{}",
                forwardingObjective, context.inPacket().receivedFrom().deviceId());
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);
    }

    /**
     * Activate the redirect to the specified host
     *
     * @param ipRedirectingTo the host packets are redirected to
     */
    @Override
    public void activateRedirect(Ip4Address ipRedirectingTo) {

    }

    /**
     * Stop the redirect of packets
     */
    @Override
    public void stopRedirect() {

    }
}
