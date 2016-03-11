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
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.*;
import org.onosproject.net.packet.*;
import org.onosproject.net.Host;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uni.wue.app.connection.Connection;
import uni.wue.app.connection.ConnectionStoreService;
import uni.wue.app.connection.DefaultConnection;
import uni.wue.app.connection.HostConnectionService;
import uni.wue.app.redirect.PacketRedirect;
import uni.wue.app.redirect.PacketRedirectService;
import uni.wue.app.service.DefaultService;
import uni.wue.app.service.DefaultServiceStore;
import uni.wue.app.service.ServiceId;
import uni.wue.app.service.ServiceStore;
import uni.wue.app.service.Service;

import java.util.Set;


/**
 * Created by lorry on 13.11.15.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class PortalManager implements PortalService{

    private final Logger log = LoggerFactory.getLogger(getClass());
    protected ApplicationId appId;


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostStore hostStore;

    // own services
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStoreService connectionStoreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected BasicRuleInstaller basicRuleInstaller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketRedirectService packetRedirectService;


    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    // ID of the portal service
    ServiceId portalId = null;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");

        basicRuleInstaller.installRules();
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();

        // initiate a test setup
        testSetup();

        log.info("Started PortalManager {}", appId.toString());
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;

        log.info("Stopped PortalManager {}", appId.toString());
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Packet processor establishing connection to the portal.
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

            // get the portal service
            Service portalService = serviceStore.getService(portalId);
            if(portalService == null){
                log.warn("PortalManager: No portal defined with ID {}", portalId.toString());
            }

            IPacket iPkt = ethPkt.getPayload();
            Ip4Address srcIp;
            if(iPkt instanceof IPv4)
                srcIp = Ip4Address.valueOf(((IPv4) iPkt).getSourceAddress());
            else{
                log.debug("PortalManager: Payload of {} is no IPv4 packet.", ethPkt.toString());
                return;
            }

            Set<Host> users = hostService.getHostsByIp(srcIp);

            // FIXME: adding rule for every IPv4 request, even if not on TpPort 80
            // FIXME: rules are stuck in PENDING_ADD state, but they are installed and do work!
            // add rules to routing devices enabling the connection between user and portal
            for(Host user : users) {
                Connection connection = new DefaultConnection(user, portalService);
                connectionStoreService.addConnection(connection);
            }

            // check if a redirect flow rule is necessary
            // (the destination of the packet differs from the portal addresses)
            Boolean addressDiffersFromPortal = true;
            for(IpAddress portalAddress : portalService.getHost().ipAddresses()){
                addressDiffersFromPortal = srcIp.equals(portalAddress) ? false : addressDiffersFromPortal;
            }
            if(addressDiffersFromPortal){
                // install redirect rules in the network device flow table
                packetRedirectService.redirectToPortal(context, portalService.getHost());
            }

            // send context to flow table, where it should be handled
            context.treatmentBuilder().setOutput(PortNumber.TABLE);
            context.send();
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

            Service portalService = new DefaultService(portalHosts.iterator().next(), TpPort.tpPort(80), "PortalService", ProviderId.NONE);
            serviceStore.addService(portalService);
            portalId = portalService.id();

            log.info(String.format("Set portal ID to {}", this.portalId.toString()));
            return true;
        }

        log.warn(String.format("Could not set portal. No host defined with IP %s!", portalIPv4.toString()));
        return false;
    }

    // better define hosts in network-cfg.json
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

            log.debug(String.format("No host with IP address {} found." +
                    "\nCreated new Host."), portalIPv4);

            return setPortal(pIp);
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
        return serviceStore.getService(portalId).getHost().mac();
    }

    /**
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    @Override
    public Set<IpAddress> getPortalIp() { return serviceStore.getService(portalId).getHost().ipAddresses(); }

    /**
     * Get the portal as host
     *
     * @return Host
     */
    @Override
    public Host getPortal() {
        return serviceStore.getService(portalId).getHost();
    }

    /**
     * Initiate a test setup
     */
    private void testSetup(){
        // just for development reasons -> better use netcfghostprovider
        setPortal("10.0.0.3");
        // add some services to the serviceStore
        Host host = hostService.getHostsByIp(IpAddress.valueOf("10.0.0.3")).iterator().next();
        Service service = new DefaultService(host, TpPort.tpPort(80), "PortalService", ProviderId.NONE);
        serviceStore.addService(service);
        host = hostService.getHostsByIp(IpAddress.valueOf("10.0.0.4")).iterator().next();
        service = new DefaultService(host, TpPort.tpPort(80), "TestService1", ProviderId.NONE);
        serviceStore.addService(service);
        host = hostService.getHostsByIp(IpAddress.valueOf("10.0.0.4")).iterator().next();
        service = new DefaultService(host, TpPort.tpPort(22), "TestService2", ProviderId.NONE);
        serviceStore.addService(service);
    }

}
