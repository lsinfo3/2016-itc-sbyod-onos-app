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

import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPacket;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.*;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.DefaultConnection;
import org.sardineproject.sbyod.service.*;
import org.sardineproject.sbyod.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 11.12.15.
 */
@Component(immediate = false, inherit = true)
@org.apache.felix.scr.annotations.Service
public class ControllerRedirect extends PacketRedirect {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PortalService portalService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;


    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    private Map<Ip4Address, Ip4Address> newToOldIpAddress;

    // source and destination pairs of changed packets
    private Map<Host, Host> primaryDst;

    @Activate
    protected void activate(){

        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();

        this.newToOldIpAddress = new HashMap<>();

        this.primaryDst = new HashMap<>();

    }

    @Deactivate
    protected void deactivate(){

        withdrawIntercepts();
        packetService.removeProcessor(processor);

        this.newToOldIpAddress = null;

        this.primaryDst = null;
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                applicationIdStore.getAppId(APPLICATION_ID), Optional.<DeviceId>empty());
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                applicationIdStore.getAppId(APPLICATION_ID), Optional.<DeviceId>empty());
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE,
                applicationIdStore.getAppId(APPLICATION_ID), Optional.<DeviceId>empty());
        selector.matchEthType(Ethernet.TYPE_ARP);
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

            if(packet.getEtherType() == Ethernet.TYPE_IPV4){
                IPv4 ipv4Packet = (IPv4) packet.getPayload();

                /*
                ### The packet is of ipv4 ethernet type, no rules have been added for it yet,
                ### otherwise it would have been handled by these rules.
                ### Therefore the connection to the portal is checked for the source host
                ### and a redirect is done to the portal.
                */

                Service portal = portalService.getPortalService();

                // if packet destination was changed and source is the portal
                if(newToOldIpAddress.keySet().contains(Ip4Address.valueOf(ipv4Packet.getDestinationAddress())) &&
                        portalService.getPortalIp().toInt() == ipv4Packet.getSourceAddress()){
                    // restore old src and destination
                    restoreSource(context);
                } else if(portalService.getPortalIp().toInt() != (ipv4Packet.getDestinationAddress())){
                    // if the packet destination differs from the portal address -> redirect packet to portal
                    redirectToPortal(context);
                } else{
                    log.warn("ControllerRedirect: Packet with portal destination was not forwarded. {}", ipv4Packet);
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

    private void redirectToPortal(PacketContext context){
        IPv4 ipv4Packet = (IPv4) context.inPacket().parsed().getPayload();

        log.info("ControllerRedirect: Redirecting destination {} of source {} to portal {}",
                ipv4Packet.getDestinationAddress(), ipv4Packet.getSourceAddress(), portalService.getPortalIp());

    }

    private void restoreSource(PacketContext context){

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
    }

    /**
     * Restores the packet source from the portal to the previous intended source
     * and send a copy of the packet
     *
     * @param context the packet context
     */
    @Override
    public void restoreSource(PacketContext context, Host portal) {
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
                                pkt.receivedFrom().deviceId().toString(), dstHost.id().toString()));
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
                }
            }
        }
    }
}
