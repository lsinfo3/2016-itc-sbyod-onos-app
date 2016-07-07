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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.configuration.ByodConfig;
import org.sardineproject.sbyod.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 11.12.15.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class ControllerRedirect implements PacketRedirectService {

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

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;


    private ReactivePacketProcessor processor;

    // the host the traffic is redirected to
    private Host redirectHost;
    // a map with all src ip and src port pairs and dst ip and dst mac pairs
    private Map<IpPortPair, IpMacPair> portToMac;

    Map<DeviceId, List<ForwardingObjective>> installedRules;

    @Activate
    protected void activate(){
        activateRedirect(hostService.getHostsByIp(Ip4Address.valueOf("10.1.0.2")).iterator().next());
    }

    @Deactivate
    protected void deactivate(){
        stopRedirect();
    }

    public void activateRedirect(Host redirectHost){
        // define the host to redirect the traffic to
        this.redirectHost = redirectHost;

        // initiate empty rules map
        installedRules = new HashMap<>();
        // install rules sending relevant packets to controller
        installRedirectRules();

        // add packet processor monitoring packets
        processor = new ReactivePacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
    }

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

        this.portToMac = null;
        this.redirectHost = null;
    }

    private void installRedirectRules(){

        ForwardingObjective.Builder port80ToControllerRule = getPort80ToControllerRule();
        ForwardingObjective.Builder portalToControllerRule = getPortalToControllerRule();

        for(Device device : deviceService.getDevices()){
            // install rule sending every unhandled traffic on port 80 to controller
            flowObjectiveService.forward(device.id(), port80ToControllerRule.add());
            if(installedRules.get(device.id()) != null) {
                installedRules.get(device.id()).add(port80ToControllerRule.remove());
            } else{
                installedRules.put(device.id(), Lists.newArrayList(port80ToControllerRule.remove()));
            }
            // fixme: check if value is null?

            // install rule sending every answer from portal received on port 80 to controller
            flowObjectiveService.forward(device.id(), portalToControllerRule.add());
            if(installedRules.get(device.id()) != null) {
                installedRules.get(device.id()).add(portalToControllerRule.remove());
            } else{
                installedRules.put(device.id(), Lists.newArrayList(portalToControllerRule.remove()));
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

    private ForwardingObjective.Builder getPortalToControllerRule(){

        //ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // TODO: find portal address!
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
                    Host portalHost = hostService.getHostsByIp(portal.ipAddress()).iterator().next();
                    // only redirect if portal is defined
                    if (portal != null) {

                        // if packet destination was changed and source is the portal on port 80
                        if (ipv4Packet.getSourceAddress() == portal.ipAddress().toInt() &&
                                tcpPacket.getSourcePort() == 80) {
                            // restore old src and destination
                            restoreSource(context, portalHost);
                        } else if (tcpPacket.getDestinationPort() == 80 &&
                                portalManager.getPortalIp().toInt() != (ipv4Packet.getDestinationAddress())) {
                            // if the packet destination differs from the portal address -> redirect packet to portal
                            redirectToPortal(context, portalHost);
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

    public void redirectToPortal(PacketContext context, Host inPortalHost){
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

            log.info("ControllerRedirect: redirectToPortal: redirected packet\n(srcIP={}, srcMac={}, dstIP={}, " +
                            "dstMac={}) -> (srcIP={}, srcMac={}, dstIP={}, dstMac={})",
                    Lists.newArrayList(
                            Ip4Address.valueOf(ipv4Packet.getSourceAddress()),
                            packet.getSourceMAC(),
                            oldIpDst,
                            packet.getDestinationMAC(),
                            Ip4Address.valueOf(ipv4Packet.getSourceAddress()),
                            packet.getSourceMAC(),
                            portal.ipAddress(),
                            portalHost.mac())
                            .toArray());

        } else if(portalHosts.isEmpty()){
            log.warn("ControllerRedirect: RedirectToPortal: no portal host found with ip={}", portal.ipAddress());
        } else{
            log.warn("ControllerRedirect: RedirectToPortal: more than one portal host found with ip={}",
                    portal.ipAddress());
        }

    }

    public void restoreSource(PacketContext context, Host portal){
        // parsing packet
        Ethernet packet = context.inPacket().parsed();
        IPv4 ipv4Packet = (IPv4) packet.getPayload();
        TCP tcpPacket = (TCP) ipv4Packet.getPayload();

        // creating the ip-port pair for the packet
        IpPortPair ipPortPair = new IpPortPair(Ip4Address.valueOf(ipv4Packet.getDestinationAddress()),
                TpPort.tpPort(tcpPacket.getDestinationPort()));
        // check if an entry was created for this ipPortPair
        if(portToMac.keySet().contains(ipPortPair)){

            // get the src ip and mac address to restore
            IpMacPair ipMacPair = portToMac.get(ipPortPair);
            Ip4Address newSrcIp = ipMacPair.getIp4Address();
            MacAddress newSrcMac = ipMacPair.getMacAddress();

            log.info("ControllerRedirect: restoreSource: restoring source\n(srcIp={}, srcMac={}, dstIp={}," +
                            " dstMac={}) ->\n(srcIP={}, srcMac={}, dstIP={}, dstMac={})",
                    Lists.newArrayList(
                            Ip4Address.valueOf(ipv4Packet.getSourceAddress()),
                            packet.getSourceMAC(),
                            Ip4Address.valueOf(ipv4Packet.getDestinationAddress()),
                            packet.getDestinationMAC(),
                            newSrcIp,
                            newSrcMac,
                            Ip4Address.valueOf(ipv4Packet.getDestinationAddress()),
                            packet.getDestinationMAC())
                            .toArray());

            // get the destination host
            Host destinationHost = hostService.getHost(HostId.hostId(packet.getDestinationMAC(),
                    VlanId.vlanId(packet.getVlanID())));

            if(destinationHost != null){

                // set the new source ip address
                ipv4Packet.setSourceAddress(newSrcIp.toInt());
                // set the new source mac address
                packet.setSourceMACAddress(newSrcMac);

                // reset the packet checksum
                ipv4Packet.resetChecksum();
                packet.resetChecksum();
                tcpPacket.resetChecksum();

                ByteBuffer buf = ByteBuffer.wrap(packet.serialize());

                // create the traffic treatment for the restored packet
                TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                        .setIpDst(Ip4Address.valueOf(ipv4Packet.getDestinationAddress()))
                        .setEthDst(packet.getDestinationMAC())
                        .setOutput(destinationHost.location().port());

                // emit the packet at the device the host is connected to
                packetService.emit(new DefaultOutboundPacket(destinationHost.location().deviceId(),
                        trafficTreatmentBuilder.build(),
                        buf));
                // block the old context
                context.block();

            } else{
                log.warn("ControllerRedirect: No destination Host found for ip={} while restoring source",
                        Ip4Address.valueOf(ipv4Packet.getSourceAddress()));
            }
        } else{
            log.warn("ControllerRedirect: No IP and Mac known for ip={} and port={} while restoring source",
                    ipPortPair.getIp4Address(), ipPortPair.getTpPort());
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
