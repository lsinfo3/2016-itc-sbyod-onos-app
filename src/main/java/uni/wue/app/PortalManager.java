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

import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.*;
import org.onosproject.net.packet.*;
import org.onosproject.net.Host;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.proxyarp.ProxyArpService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.ByteBuffer;
import java.sql.Time;
import java.util.HashMap;
import java.util.HashSet;
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
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostStore hostStore;

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    protected ApplicationId appId;

    // host of the captive portal
    private Host portal;

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
    public boolean setPortal(String pIp){
        checkNotNull(pIp, "Portal IPv4 address can not be null");
        // parse IP address
        Ip4Address portalIPv4 = Ip4Address.valueOf(pIp);

        // find hosts with portal IP address
        Set<Host> portalHosts = hostService.getHostsByIp(portalIPv4);
        if(portalHosts.size() == 1) {
            this.portal = portalHosts.iterator().next();
            log.debug(String.format("Set portal to %s", this.portal.id().toString()));
            return true;
        }

        log.warn(String.format("Could not set portal. No host defined with IP %s!", portalIPv4.toString()));
        return false;
    }

    @Override
    public boolean setPortal(String pIp, String pMac, String dId, String dPort) {
        checkNotNull(pIp, "Portal IPv4 address can not be null!");
        Ip4Address portalIPv4 = Ip4Address.valueOf(pIp);

        // find host with portal IP address
        Set<Host> portalHosts = hostService.getHostsByIp(portalIPv4);
        if(portalHosts.size() == 1) {
            return setPortal(pIp);
        } else if(portalHosts.size() == 0){

            checkNotNull(pMac, "Portal MAC address can not be null!");
            MacAddress portalMac = MacAddress.valueOf(pMac);
            checkNotNull(dId, "Device ID can not be null!");
            Device switchDevice = deviceService.getDevice(DeviceId.deviceId(dId));
            checkNotNull(dPort, "Device port number can not be null!");
            PortNumber portNumber = PortNumber.portNumber(Long.valueOf(dPort));

            // Create new Host:
            HostLocation hostLocation = new HostLocation(switchDevice.id(), portNumber, System.nanoTime());
            HostDescription hostDescription = new DefaultHostDescription(portalMac, VlanId.NONE, hostLocation,
                    Sets.newHashSet(portalIPv4));

            hostStore.createOrUpdateHost(ProviderId.NONE, HostId.hostId(portalMac, VlanId.NONE), hostDescription, true);
            portal = hostService.getHostsByIp(portalIPv4).iterator().next();

            log.debug(String.format("No host with IP address %s found." +
                    "\nCreated new Host %s.", portalIPv4, portal.toString()));

            return true;
        }

        log.warn(String.format("Could not set portal. More than one hosts with IP %s found.", portalIPv4.toString()));
        return false;
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
