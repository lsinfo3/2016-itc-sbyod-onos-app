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
 */

package org.sardineproject.sbyod.service;

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.*;
import org.onlab.packet.ndp.NeighborDiscoveryOptions;
import org.onlab.packet.ndp.NeighborSolicitation;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketService;
import org.sardineproject.sbyod.PortalManager;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 13.07.16.
 */
@Component
@org.apache.felix.scr.annotations.Service
public class HostArp implements HostArpService{

    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EdgePortService edgePortService;

    private static final byte[] ZERO_MAC_ADDRESS = MacAddress.ZERO.toBytes();

    //private PacketService packetService;
    //private HostManager hostManager;
    //private InterfaceService interfaceService;
    //private EdgePortService edgePortService;

    public HostArp(PacketService packetService, InterfaceService interfaceService, EdgePortService edgePortService){

        this.packetService = packetService;
        this.interfaceService = interfaceService;
        this.edgePortService = edgePortService;
    }

    /**
     * Sends an ARP or NDP request for the given IP address on every interface.
     *
     * @param targetIp IP address to send the request for
     */
    public void sendRequest(IpAddress targetIp) {
        for(Interface intf : interfaceService.getInterfaces()) {

            if (intf == null) {
                return;
            }

            if (!edgePortService.isEdgePoint(intf.connectPoint())) {
                // log.warn("Attempt to send probe out non-edge port: {}", intf);
                return;
            }

            for (InterfaceIpAddress ia : intf.ipAddresses()) {
                if (ia.subnetAddress().contains(targetIp)) {
                    sendProbe(intf.connectPoint(), targetIp, ia.ipAddress(),
                            intf.mac(), intf.vlan());
                }
            }
        }
    }

    private void sendProbe(ConnectPoint connectPoint,
                           IpAddress targetIp,
                           IpAddress sourceIp, MacAddress sourceMac,
                           VlanId vlan) {
        Ethernet probePacket = null;

        if (targetIp.isIp4()) {
            // IPv4: Use ARP
            probePacket = buildArpRequest(targetIp, sourceIp, sourceMac, vlan);
        } else {
            // IPv6: Use Neighbor Discovery
            probePacket = buildNdpRequest(targetIp, sourceIp, sourceMac, vlan);
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(connectPoint.port())
                .build();

        OutboundPacket outboundPacket =
                new DefaultOutboundPacket(connectPoint.deviceId(), treatment,
                        ByteBuffer.wrap(probePacket.serialize()));

        packetService.emit(outboundPacket);
        log.info("HostArp: Sent ARP request (ConnectionPoint={}, targetIP={}, sourceIp={}, sourceMac={}, Vlan={}",
                Lists.newArrayList(
                        connectPoint.toString(),
                        targetIp,
                        sourceIp,
                        sourceMac,
                        vlan).toArray());
    }

    private Ethernet buildArpRequest(IpAddress targetIp, IpAddress sourceIp,
                                     MacAddress sourceMac, VlanId vlan) {

        ARP arp = new ARP();
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setProtocolAddressLength((byte) IpAddress.INET_BYTE_LENGTH)
                .setOpCode(ARP.OP_REQUEST);

        arp.setSenderHardwareAddress(sourceMac.toBytes())
                .setSenderProtocolAddress(sourceIp.toOctets())
                .setTargetHardwareAddress(ZERO_MAC_ADDRESS)
                .setTargetProtocolAddress(targetIp.toOctets());

        Ethernet ethernet = new Ethernet();
        ethernet.setEtherType(Ethernet.TYPE_ARP)
                .setDestinationMACAddress(MacAddress.BROADCAST)
                .setSourceMACAddress(sourceMac)
                .setPayload(arp);

        if (!vlan.equals(VlanId.NONE)) {
            ethernet.setVlanID(vlan.toShort());
        }

        ethernet.setPad(true);

        return ethernet;
    }

    private Ethernet buildNdpRequest(IpAddress targetIp, IpAddress sourceIp,
                                     MacAddress sourceMac, VlanId vlan) {

        // Create the Ethernet packet
        Ethernet ethernet = new Ethernet();
        ethernet.setEtherType(Ethernet.TYPE_IPV6)
                .setDestinationMACAddress(MacAddress.BROADCAST)
                .setSourceMACAddress(sourceMac);
        if (!vlan.equals(VlanId.NONE)) {
            ethernet.setVlanID(vlan.toShort());
        }

        //
        // Create the IPv6 packet
        //
        // TODO: The destination IP address should be the
        // solicited-node multicast address
        IPv6 ipv6 = new IPv6();
        ipv6.setSourceAddress(sourceIp.toOctets());
        ipv6.setDestinationAddress(targetIp.toOctets());
        ipv6.setHopLimit((byte) 255);

        // Create the ICMPv6 packet
        ICMP6 icmp6 = new ICMP6();
        icmp6.setIcmpType(ICMP6.NEIGHBOR_SOLICITATION);
        icmp6.setIcmpCode((byte) 0);

        // Create the Neighbor Solicitation packet
        NeighborSolicitation ns = new NeighborSolicitation();
        ns.setTargetAddress(targetIp.toOctets());
        ns.addOption(NeighborDiscoveryOptions.TYPE_SOURCE_LL_ADDRESS,
                sourceMac.toBytes());

        icmp6.setPayload(ns);
        ipv6.setPayload(icmp6);
        ethernet.setPayload(ipv6);

        return ethernet;
    }
}
