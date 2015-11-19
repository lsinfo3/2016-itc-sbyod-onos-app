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

import jdk.nashorn.internal.objects.Global;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;


/**
 * Created by lorry on 13.11.15.
 */
@Component(immediate = true)
@Service
public class PortalManager implements PortalService{

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    protected ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    private MacAddress portalMac;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");

        packetService.addProcessor(processor, PacketProcessor.director(3));

        log.info("Started PortalManager");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
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

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }

            EthType type = new EthType(ethPkt.getEtherType());
            System.out.println("Packet with ethType: " + type.toString());

            if(ethPkt.getEtherType() == Ethernet.TYPE_IPV4 && portalMac != null){
                MacAddress oldMac = ethPkt.getDestinationMAC();
                ethPkt.setDestinationMACAddress(portalMac);
                System.out.println("Changed destination mac address of packet from " + oldMac.toString()
                        + " to " + portalMac.toString());
                return;
            }
        }
    }

    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    @Override
    public void setPortal(String portal) {
        checkNotNull(portal, "Portal mac address can not be null");
        //portalMac = new MacAddress(parseMacAddress(portal));
        portalMac = MacAddress.valueOf(portal);
        System.out.println(String.format("Set portal mac address to %s", portalMac.toString()));
    }

    private static byte[] parseMacAddress(String macAddress) {
        String[] bytes = macAddress.split(":");
        byte[] parsed = new byte[bytes.length];

        for (int x = 0; x < bytes.length; x++) {
            BigInteger temp = new BigInteger(bytes[x], 16);
            byte[] raw = temp.toByteArray();
            parsed[x] = raw[raw.length - 1];
        }
        return parsed;
    }

}
