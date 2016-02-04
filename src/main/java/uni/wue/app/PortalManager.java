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
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.InterfaceIpAddress;
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
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketRedirectService packetRedirectService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    protected ApplicationId appId;

    // host of the captive portal
    private Host portal;
    // portal IP address
    private Ip4Address portalIp;
    // service to redirect the incoming packets
    //private PacketRedirectService packetRedirectService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");
        packetService.addProcessor(processor, PacketProcessor.director(1));

        //packetRedirectService = DefaultServiceDirectory.getService(PacketRedirectService.class);

        log.info("Started PortalManager");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;


        packetRedirectService = null;

        log.info("Stopped PortalManager");
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

            // try to update portal-id
            if(portal == null){
                setPortal(portalIp.toString());
            }

            // TODO: add IPv6 support?
            //if portal is defined -> change destination of packet with ipv4 type
            if(portal != null && ethPkt.getEtherType() == Ethernet.TYPE_IPV4){

                // filter out packets from the portal
                if(ethPkt.getSourceMAC().equals(portal.mac())){
                    // restore the packet source from the portal address to the previous intended address
                    packetRedirectService.restoreSource(context, portal);
                } else if(ethPkt.getDestinationMAC().equals(portal.mac())){
                    //do nothing
                } else {
                    // change the destination address of the packet to the portal address
                    packetRedirectService.redirectToPortal(context, portal);
                }
                return;
            }
            // portal host is unknown but IP is set
            else if(portalIp != null && ethPkt.getEtherType() == Ethernet.TYPE_IPV4){
                IPv4 ipPkt = (IPv4)ethPkt.getPayload();
                // packets not from portal
                if(!Ip4Address.valueOf(ipPkt.getSourceAddress()).equals(portalIp)) {
                    // change destination ip and flood
                    ((PacketRedirect) packetRedirectService).FloodPacket(context, portalIp);
                }
            }
            return;
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

    @Override
    public void setPortal(String portal) {
        checkNotNull(portal, "Portal IPv4 address can not be null");

        Ip4Address portalIPv4 = Ip4Address.valueOf(portal);
        portalIp = portalIPv4;

        // find host of portal IP address
        Set<Host> portalHosts = hostService.getHostsByIp(portalIPv4);
        if(portalHosts.size() >= 2){
            log.debug(String.format("More than one Host with IP address %s!\nCould not assign Host to IP address.",portalIPv4));
        } else if(portalHosts.size() == 0){
            log.debug(String.format("No host with IP address %s found.", portalIPv4));
            log.debug(String.format("Set portal IPv4 address to %s", portalIPv4.toString()));
        } else{
            this.portal = portalHosts.iterator().next();
            log.debug(String.format("Set portal to %s", this.portal.id().toString()));
        }
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
