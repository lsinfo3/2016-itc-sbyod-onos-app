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

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.*;
import org.sardineproject.sbyod.portal.PortalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Created by lorry on 11.12.15.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class ControllerRedirect implements PacketRedirectService {

    private static final int REDIRECT_PRIORITY = 200;
    private static final String APPLICATION_ID = PortalService.APP_ID;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final byte TCP_FLAG_MASK_SYN = 0x02;
    public static final byte TCP_FLAG_MASK_RST = 0x04;
    public static final byte TCP_FLAG_MASK_PSH = 0x08;
    public static final byte TCP_FLAG_MASK_ACK = 0x10;

    public static final String HTTP_REDIRECT = "HTTP/1.1 302 Found\r\n"+
            "Location: https://portal.s-byod.de/\r\n\r\n";

    private static int SEQUENCE_NUMBER = 0;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;


    private ReactivePacketProcessor processor;

    // mapping the installed flow rules to the device ID for removal at deactivation
    Map<DeviceId, List<ForwardingObjective>> installedRules;

    @Activate
    protected void activate(){
    }

    @Deactivate
    protected void deactivate(){
        stopRedirect();
    }


    /**
     * Activate the redirect to the specified host
     */
    public void activateRedirect(){
                // initiate empty rules map
                installedRules = new HashMap<>();
                // install rules sending relevant packets to controller
                installRedirectRules();

                // add packet processor monitoring packets
                processor = new ReactivePacketProcessor();
                packetService.addProcessor(processor, PacketProcessor.director(2));
                requestIntercepts();

                log.info("ControllerRedirect: activated!");
    }

    /**
     * Stop the redirect of packets
     */
    public void stopRedirect(){

        // remove packet processor
        if(processor != null) {
            withdrawIntercepts();
            packetService.removeProcessor(processor);
            processor = null;
        }

        // remove installed redirect rules
        if(installedRules != null) {
            for (DeviceId deviceId : installedRules.keySet()) {
                installedRules.get(deviceId)
                        .forEach(fo -> flowObjectiveService.forward(deviceId, fo));
            }
            installedRules = null;
        }

        this.installedRules = null;

        log.info("ControllerRedirect: stopped!");
    }

    /**
     * Install flow rules on network switches sending traffic with TCP destination port 80 and
     * traffic with source TCP port 80 and source IP address of the host redirecting to,
     * to the controller.
     */
    private void installRedirectRules(){

        ForwardingObjective.Builder port80ToControllerRule = getPort80ToControllerRule();

            for (Device device : deviceService.getDevices()) {
                // install rule sending every unhandled traffic on port 80 to controller
                flowObjectiveService.forward(device.id(), port80ToControllerRule.add());

                // save installed rules in map
                if (installedRules.get(device.id()) != null) {
                    installedRules.get(device.id()).add(port80ToControllerRule.remove());
                } else {
                    installedRules.put(device.id(), Lists.newArrayList(port80ToControllerRule.remove()));
                }
            }
    }

    private ForwardingObjective.Builder getPort80ToControllerRule(){
        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpDst(TpPort.tpPort(80));

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.CONTROLLER);

        return DefaultForwardingObjective.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .withPriority(REDIRECT_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makePermanent();
    }

    /**
     * Request packet in of IPv4 packets via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                applicationIdStore.getAppId(APPLICATION_ID), Optional.<DeviceId>empty());
    }

    /**
     * Cancel request for IPv4 packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE,
                applicationIdStore.getAppId(APPLICATION_ID), Optional.<DeviceId>empty());
    }

    /**
     * Packet processor establishing connection to the portal.
     * Also doing the redirect if packets with no registered destination are received.
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
            Ethernet packet = context.inPacket().parsed();

            if (packet == null) {
                return;
            }

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(packet)) {
                return;
            }

            if(packet.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) packet.getPayload();

                if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_TCP) {

                    /*
                    ### The packet is of ipv4 ethernet type, no rules have been added for it yet,
                    ### otherwise it would have been handled by these rules.
                    ### Therefore a redirect is done to the portal.
                    */
                    injectRedirect(context);
                }
            }
        }
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

    private void injectRedirect(PacketContext context) {

        // parse packets
        Ethernet packet = context.inPacket().parsed();
        IPv4 ipv4Packet = (IPv4) packet.getPayload();
        TCP tcpPacket = (TCP) ipv4Packet.getPayload();

        log.info("ControllerRedirect: Redirect called. SrcIp: {} -> DstIp: {}",
                IpAddress.valueOf(ipv4Packet.getSourceAddress()), IpAddress.valueOf(ipv4Packet.getDestinationAddress()));

        // save source info
        Integer clientIP = ipv4Packet.getSourceAddress();
        MacAddress clientMAC = packet.getSourceMAC();
        Integer clientPort = tcpPacket.getSourcePort();

        // redirect the packet back
        ipv4Packet.setSourceAddress(ipv4Packet.getDestinationAddress());
        packet.setSourceMACAddress(packet.getDestinationMAC());
        tcpPacket.setSourcePort(tcpPacket.getDestinationPort());

        // set new destination address
        ipv4Packet.setDestinationAddress(clientIP);
        packet.setDestinationMACAddress(clientMAC);
        tcpPacket.setDestinationPort(clientPort);

        // the tcp flags of the packet as short
        short tcpFlags = tcpPacket.getFlags();


        if (tcpFlags == (short)TCP_FLAG_MASK_SYN) {
            // packet has only SYN flag set


            // ### respond with a SYN ACK ###
            SEQUENCE_NUMBER = 0;
            tcpPacket.setFlags((short) (TCP_FLAG_MASK_SYN | TCP_FLAG_MASK_ACK))
                    .setAcknowledge(tcpPacket.getSequence() + 1)
                    .setSequence(SEQUENCE_NUMBER++);

            sendPacket(context);
        } else if((tcpFlags & TCP_FLAG_MASK_PSH) == (short)TCP_FLAG_MASK_PSH) {
            // packet has PSH flag set (probably GET request)

            // calculate the sequence number to acknowledge
            Data dataPayload = (Data) tcpPacket.getPayload();
            Integer acknowledgmentNumber = tcpPacket.getSequence() + dataPayload.getData().length;


            // ### acknowledge the received packet ###
            tcpPacket.setFlags((short) TCP_FLAG_MASK_ACK)
                    .setAcknowledge(acknowledgmentNumber)
                    .setSequence(SEQUENCE_NUMBER);
            // no payload
            tcpPacket.setPayload(new Data());
            sendPacket(context);


            // ### send HTTP redirect ###
            tcpPacket.setFlags((short) TCP_FLAG_MASK_ACK)
                    .setAcknowledge(acknowledgmentNumber)
                    .setSequence(SEQUENCE_NUMBER);
            // http 302 redirect as payload
            Data packetData = new Data();
            packetData.setData(HTTP_REDIRECT.getBytes());
            tcpPacket.setPayload(packetData);
            // calculate new sequence number
            SEQUENCE_NUMBER += packetData.getData().length;
            sendPacket(context);


            // ### send RST ###
            tcpPacket.setFlags((short) TCP_FLAG_MASK_RST)
                    .setAcknowledge(acknowledgmentNumber)
                    .setSequence(SEQUENCE_NUMBER);
            // no payload
            tcpPacket.setPayload(new Data());
            sendPacket(context);

        } else{
            return;
        }
        // block the old context
        context.block();
    }

    private void sendPacket(PacketContext context){

        // parse packets
        Ethernet packet = context.inPacket().parsed();
        IPv4 ipv4Packet = (IPv4) packet.getPayload();
        TCP tcpPacket = (TCP) ipv4Packet.getPayload();

        // reset packet checksum
        ipv4Packet.resetChecksum();
        packet.resetChecksum();
        tcpPacket.resetChecksum();

        ByteBuffer buf = ByteBuffer.wrap(packet.serialize());

        // send the packet back to the client
        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setIpDst(Ip4Address.valueOf(ipv4Packet.getDestinationAddress()))
                .setEthDst(packet.getDestinationMAC())
                .setOutput(context.inPacket().receivedFrom().port());

        // emit the packet at the device the host is connected to
        packetService.emit(new DefaultOutboundPacket(
                        context.inPacket().receivedFrom().deviceId(),
                        trafficTreatmentBuilder.build(),
                        buf)
        );
    }

}
