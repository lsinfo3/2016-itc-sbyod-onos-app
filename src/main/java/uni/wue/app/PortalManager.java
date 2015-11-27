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
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.packet.*;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Set;


/**
 * Created by lorry on 13.11.15.
 */
@Component(immediate = true)
@Service
public class PortalManager implements PortalService{

    private final Logger log = LoggerFactory.getLogger(getClass());

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

    private MacAddress portalMac;

    private IpAddress portalIp;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");

        packetService.addProcessor(processor, PacketProcessor.director(3));

        log.info("Started PortalManager");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
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

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            //OutboundPacket outPkt = context.outPacket();
            //outPkt.treatment();

            if (ethPkt == null) {
                return;
            }

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }

            //print out the ethernet type of the packet
            EthType type = new EthType(ethPkt.getEtherType());
            log.debug("Packet with ethType: " + type.toString());

            //change destination of packet with ipv4 type and if portal is defined
            if(ethPkt.getEtherType() == Ethernet.TYPE_IPV4 && portalMac != null){
                //do not change packets from or to portal device
                if(ethPkt.getSourceMAC().equals(portalMac) || ethPkt.getDestinationMAC().equals(portalMac)){
                    log.debug("Did not change mac address of source "
                            + (ethPkt.getSourceMAC()).toString());
                    return;
                } else {
                    MacAddress oldMac = ethPkt.getDestinationMAC();
                    //ethPkt.setDestinationMACAddress(portalMac);
                    //context.treatmentBuilder().setIpDst();

//                    IPv4 ipv4 = (IPv4) ethPkt.getPayload();
//                    ipv4.setDestinationAddress(portalIp.getIp4Address().toInt());
//                    ethPkt.setPayload(ipv4);

                    log.debug("Changed packet destination mac address from " + oldMac.toString()
                            + " to " + portalMac.toString());


                    //send the packet to destination port of portal
                    packetOut(context);

                    return;
                }
            }
            return;
        }
    }

    // Sends a packet out to its destination.
    private void packetOut(PacketContext context) {

        InboundPacket pkt = context.inPacket();
        Ethernet ethPkt = pkt.parsed();
        HostId dstId = HostId.hostId(portalMac);

        context.treatmentBuilder().setIpDst(portalIp.getIp4Address())
                .setEthDst(portalMac);

        // Do we know who this is for? If not, flood and bail.
        Host dst = hostService.getHost(dstId);
        if (dst == null) {
            //flood(context);
            return;
        }

        // Are we on an edge switch that our destination is on? If so,
        // simply forward out to the destination and bail.
        if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
            if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                System.out.println("Send context to port " + dst.location().port().toString());
                context.treatmentBuilder().setOutput(dst.location().port());
                context.send();
            }
            return;
        }

        // Otherwise, get a set of paths that lead from here to the
        // destination edge switch.
        Set<Path> paths =
                topologyService.getPaths(topologyService.currentTopology(),
                        pkt.receivedFrom().deviceId(),
                        dst.location().deviceId());
        if (paths.isEmpty()) {
            // If there are no paths, flood and bail.
            //flood(context);
            return;
        }

        // Otherwise, pick a path that does not lead back to where we
        // came from; if no such path, flood and bail.
        Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());
        if (path == null) {
            Object[] pathDetails = {pkt.receivedFrom(), ethPkt.getSourceMAC(), ethPkt.getDestinationMAC()};
            log.warn("Don't know where to go from here {} for {} -> {}", pathDetails);
            //flood(context);
            return;
        }

        // Otherwise forward and be done with it.
        context.treatmentBuilder().setOutput(path.src().port());
        context.send();

//        ByteBuffer buf = ByteBuffer.wrap(context.inPacket().parsed().serialize());
//
//        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
//        builder.setOutput(outPortNumber).setIpDst(portalIp.getIp4Address()).setEthDst(portalMac);
//        packetService.emit(new DefaultOutboundPacket(path.src().deviceId(),
//                builder.build(), buf));

    }

    // Selects a path from the given set that does not lead back to the
    // specified port if possible.
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
        Set<Host> hosts = hostService.getHostsByMac(portalMac);
        if(hosts == null)
            log.debug(String.format("Host ip of mac-address %s can not be found", portal));
        else
            for(Host host : hosts){
                for(IpAddress ip : host.ipAddresses()){
                    portalIp = ip;
                    log.debug(String.format("Portal ip set to %s", ip.toString()));
                }
            }

        System.out.println(String.format("Set portal mac address to %s", portalMac.toString()));
    }

    /**
     * Get the Mac Address of the portal
     *
     * @return MacAddress
     */
    @Override
    public MacAddress getPortalMac() {
        return new MacAddress(portalMac.toBytes());
    }

    /**
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    @Override
    public IpAddress getPortalIp() {
        return portalIp;
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
