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
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.*;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.configuration.ByodConfig;
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
@Component(immediate = true, inherit = true)
@org.apache.felix.scr.annotations.Service
public class ControllerRedirect extends PacketRedirect {

    private static final int REDIRECT_PRIORITY = 200;
    private static final String APPLICATION_ID = PortalService.APP_ID;
    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PortalService portalManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;


    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    private Map<IpPortPair, IpMacPair> portToMac;

    private Map<PacketValues, PacketValues> newToOldIpAddress;

    private IpCounter ipCounter;

    // source and destination pairs of changed packets
    private Map<Host, Host> primaryDst;

    @Activate
    protected void activate(){

        // install rules sending relevant packets to controller
        installRedirectRules();

        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();

        this.newToOldIpAddress = new HashMap<>();

        this.portToMac = new HashMap<>();

        this.primaryDst = new HashMap<>();

        // starting at ip *.*.*.1
        ipCounter = new IpCounter();

    }

    @Deactivate
    protected void deactivate(){

        // TODO: remove flows

        withdrawIntercepts();
        packetService.removeProcessor(processor);

        this.newToOldIpAddress = null;

        this.portToMac = null;

        this.primaryDst = null;
    }

    private void installRedirectRules(){

        for(Device device : deviceService.getDevices()){
            // install rule sending every unhandled traffic on port 80 to controller
            flowObjectiveService.forward(device.id(), getPort80ToControllerRule().add());
            // install rule sending every answer from portal received on port 80 to controller
            ForwardingObjective.Builder portalToControllerRule = getPortalToControllerRule();
            if(portalToControllerRule != null)
                flowObjectiveService.forward(device.id(), portalToControllerRule.add());
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

    private ForwardingObjective.Builder getPortalToControllerRule(){

        //ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        Ip4Address portalIp = Ip4Address.valueOf("10.1.0.2");
        if(portalIp != null) {

            TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPProtocol(IPv4.PROTOCOL_TCP)
                    .matchTcpSrc(TpPort.tpPort(80))
                    .matchIPSrc(portalIp.toIpPrefix());

            TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                    .setOutput(PortNumber.CONTROLLER);

            return DefaultForwardingObjective.builder()
                    .withSelector(trafficSelectorBuilder.build())
                    .withTreatment(trafficTreatmentBuilder.build())
                    .withPriority(REDIRECT_PRIORITY)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                    .makePermanent();
        } else{
            log.warn("ControllerRedirect: No default IP defined in config file.");
            return null;
        }
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

            if(packet.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Packet = (IPv4) packet.getPayload();

                if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_TCP) {
                    TCP tcpPacket = (TCP) ipv4Packet.getPayload();

                    /*
                    ### The packet is of ipv4 ethernet type, no rules have been added for it yet,
                    ### otherwise it would have been handled by these rules.
                    ### Therefore the connection to the portal is checked for the source host
                    ### and a redirect is done to the portal.
                    */

                    Service portal = portalManager.getPortalService();
                    // only redirect if portal is defined
                    if (portal != null) {

                        // if packet destination was changed and source is the portal on port 80
                        if (ipv4Packet.getSourceAddress() == portal.ipAddress().toInt() &&
                                tcpPacket.getSourcePort() == 80) {
                            // restore old src and destination
                            restoreSource(context);
                        } else if (tcpPacket.getDestinationPort() == 80 &&
                                portalManager.getPortalIp().toInt() != (ipv4Packet.getDestinationAddress())) {
                            // if the packet destination differs from the portal address -> redirect packet to portal
                            redirectToPortal(context);
                        } else if (tcpPacket.getDestinationPort() == 80){
                            log.warn("ControllerRedirect: Packet with portal destination was not forwarded. {}", ipv4Packet);
                        }
                    } else {
                        log.warn("ControllerRedirect: No portal defined.");
                    }
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
        Ethernet packet = context.inPacket().parsed();
        IPv4 ipv4Packet = (IPv4) packet.getPayload();
        TCP tcpPacket = (TCP) ipv4Packet.getPayload();

        log.info("ControllerRedirect: Redirecting (source IP={}, destination IP={}) -> to portal IP={}",
                Ip4Address.valueOf(ipv4Packet.getSourceAddress()), Ip4Address.valueOf(ipv4Packet.getDestinationAddress()), portalManager.getPortalIp());

        // save old values
        Ip4Address oldIpDst = Ip4Address.valueOf(ipv4Packet.getDestinationAddress());
        MacAddress oldMacDst = packet.getDestinationMAC();
        // save old values in packet value class for later reconstruction

        // get the host where the portal is running on
        Service portal = portalManager.getPortalService();
        Set<Host> portalHosts = hostService.getHostsByIp(portal.ipAddress());
        if(portalHosts.size() == 1){
            Host portalHost = portalHosts.iterator().next();

            // set the portal ip the packet is redirected to
            ipv4Packet.setDestinationAddress(portal.ipAddress().toInt());
            // set the mac address of the portal as destination
            packet.setDestinationMACAddress(portalHost.mac());

            // reset packet checksum
            ipv4Packet.resetChecksum();
            packet.resetChecksum();
            tcpPacket.resetChecksum();
            // create a buffer for the serialized packet
            ByteBuffer buf = ByteBuffer.wrap(packet.serialize());

            // set up the traffic management
            TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder()
                    .setIpDst(portal.ipAddress())
                    .setEthDst(portalHost.mac())
                    .setOutput(portalHost.location().port());

            //send new packet to the device where the portal is connected to
            packetService.emit(new DefaultOutboundPacket(portalHost.location().deviceId(),
                    builder.build(), buf));
            context.block();

            // save src ip - port and dst ip - mac pairs
            portToMac.put(new IpPortPair(Ip4Address.valueOf(ipv4Packet.getSourceAddress()),
                    TpPort.tpPort(tcpPacket.getSourcePort())),
                    new IpMacPair(oldIpDst, oldMacDst));

            log.info("ControllerRedirect: redirectToPortal: redirected packet with (srcIP={}, dstIP={}, srcMac={}, " +
                            "dstMac={}) -> (srcIP={}, dstIP={}, srcMac={}, dstMac={})",
                    Lists.newArrayList(Ip4Address.valueOf(ipv4Packet.getSourceAddress()), oldIpDst, packet.getSourceMAC(), packet.getDestinationMAC(),
                            Ip4Address.valueOf(ipv4Packet.getSourceAddress()), portal.ipAddress(), packet.getSourceMAC(), portalHost.mac()).toArray());

        } else if(portalHosts.isEmpty()){
            log.warn("ControllerRedirect: RedirectToPortal: no portal host found with ip={}", portal.ipAddress());
        } else{
            log.warn("ControllerRedirect: RedirectToPortal: more than one portal host found with ip={}",
                    portal.ipAddress());
        }

    }

    private void restoreSource(PacketContext context){
        Ethernet packet = context.inPacket().parsed();
        IPv4 ipv4Packet = (IPv4) packet.getPayload();
        TCP tcpPacket = (TCP) ipv4Packet.getPayload();

        log.info("ControllerRedirect: Restoring source IP={}, destination IP={}",
                Ip4Address.valueOf(ipv4Packet.getSourceAddress()), Ip4Address.valueOf(ipv4Packet.getDestinationAddress()));

        IpPortPair ipPortPair = new IpPortPair(Ip4Address.valueOf(ipv4Packet.getDestinationAddress()),
                TpPort.tpPort(tcpPacket.getDestinationPort()));
        if(portToMac.keySet().contains(ipPortPair)){
            // save old mac and ip destination
            IpMacPair ipMacPair = portToMac.get(ipPortPair);
            Ip4Address newSrcIp = ipMacPair.getIp4Address();
            MacAddress newSrcMac = ipMacPair.getMacAddress();

            Host destinationHost = hostService.getHost(HostId.hostId(packet.getDestinationMAC(),
                    VlanId.vlanId(packet.getVlanID())));
            if(destinationHost != null){
                ipv4Packet.setSourceAddress(newSrcIp.toInt());
                packet.setSourceMACAddress(newSrcMac);
                ipv4Packet.resetChecksum();
                packet.resetChecksum();
                tcpPacket.resetChecksum();
                ByteBuffer buf = ByteBuffer.wrap(packet.serialize());

                TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                        .setIpDst(Ip4Address.valueOf(ipv4Packet.getDestinationAddress()))
                        .setEthDst(packet.getDestinationMAC())
                        .setOutput(destinationHost.location().port());

                packetService.emit(new DefaultOutboundPacket(destinationHost.location().deviceId(),
                        trafficTreatmentBuilder.build(),
                        buf));

                context.block();
            } else{
                log.warn("ControllerRedirect: No destination Host found while restoring source");
            }
        } else{
            log.warn("ControllerRedirect: No IP and Mac known for this IP and port while restoring source");
        }
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

    private class IpCounter{
        private int count;

        public IpCounter(){
            count = 1;
        }

        public int getCount(){
            if(count <= 254) {
                return count++;
            } else{
                count = 1;
                return count;
            }
        }
    }

    private class PacketValues {
        private Ip4Address ipSrc;
        private Ip4Address ipDst;
        private MacAddress macSrc;
        private MacAddress macDst;

        public PacketValues(Ip4Address ipSrc, Ip4Address ipDst, MacAddress macSrc, MacAddress macDst){
            this.ipSrc = ipSrc;
            this.ipDst = ipDst;
            this.macSrc = macSrc;
            this.macDst = macDst;
        }

        public Ip4Address getIpSrc(){
            return ipSrc;
        }

        public Ip4Address getIpDst(){
            return ipDst;
        }

        public MacAddress getMacSrc(){
            return macSrc;
        }

        public MacAddress getMacDst(){
            return macDst;
        }
    }

    private class IpPortPair{

        private Ip4Address ip4Address;
        private TpPort tpPort;

        public IpPortPair(Ip4Address ip4Address, TpPort tpPort){
            this.ip4Address = ip4Address;
            this.tpPort = tpPort;
        }

        public Ip4Address getIp4Address() {
            return ip4Address;
        }

        public TpPort getTpPort() {
            return tpPort;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IpPortPair that = (IpPortPair) o;

            if (ip4Address != null ? !ip4Address.equals(that.ip4Address) : that.ip4Address != null) return false;
            return !(tpPort != null ? !tpPort.equals(that.tpPort) : that.tpPort != null);

        }

        @Override
        public int hashCode() {
            int result = ip4Address != null ? ip4Address.hashCode() : 0;
            result = 31 * result + (tpPort != null ? tpPort.hashCode() : 0);
            return result;
        }
    }

    private class IpMacPair{

        private Ip4Address ip4Address;
        private MacAddress macAddress;

        public IpMacPair(Ip4Address ip4Address, MacAddress macAddress){
            this.ip4Address = ip4Address;
            this.macAddress = macAddress;
        }

        public Ip4Address getIp4Address() {
            return ip4Address;
        }

        public MacAddress getMacAddress() {
            return macAddress;
        }
    }
}
