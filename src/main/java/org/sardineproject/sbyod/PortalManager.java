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
package org.sardineproject.sbyod;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.*;
import org.onosproject.net.packet.*;
import org.onosproject.net.Host;
import org.onosproject.net.provider.ProviderId;
import org.sardineproject.sbyod.service.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.connection.DefaultConnection;
import org.sardineproject.sbyod.redirect.PacketRedirectService;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.ServiceStore;
import org.sardineproject.sbyod.service.Service;
//import org.osgi.service.component.ComponentContext;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;


/**
 * Created by lorry on 13.11.15.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class PortalManager implements PortalService{

    private final Logger log = LoggerFactory.getLogger(getClass());
    public ApplicationId appId;


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

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;

    // own services
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected BasicRuleInstaller basicRuleInstaller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketRedirectService packetRedirectService;

    // Portal configuration

    private final InternalConfigListener cfgListener = new InternalConfigListener();

    private final Set<ConfigFactory> factories = ImmutableSet.of(
            new ConfigFactory<ApplicationId, ByodConfig>(APP_SUBJECT_FACTORY,
                    ByodConfig.class,
                    "sbyod"){
                @Override
                public ByodConfig createConfig(){
                    return new ByodConfig();
                }
            }
    );

    // Portal configuration values
    // Hardcoded values are default values.

    private Ip4Address portalIp = Ip4Address.valueOf("10.0.0.3");

    private TpPort portalPort = TpPort.tpPort(3000);

    // make sure no new host is added to the host service, as long as
    // the hosts are iterated
    private final Lock hostLock = new ReentrantLock();

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    // ID of the portal service
    ServiceId portalId = null;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication(PortalService.APP_ID);

        // configuration listener
        cfgService.addListener(cfgListener);
        factories.forEach(cfgService::registerConfigFactory);
        cfgListener.reconfigureNetwork(cfgService.getConfig(appId, ByodConfig.class));

        basicRuleInstaller.installRules();
        // TODO: Add topology listener -> adding basic rules if new device connected.
        packetService.addProcessor(processor, PacketProcessor.director(2));

        // host listener
        hostService.addListener(new PortalConnectionHostListener());

        log.info("Started PortalManager {}", appId.toString());
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        factories.forEach(cfgService::unregisterConfigFactory);

        withdrawIntercepts();
        packetService.removeProcessor(processor);
        processor = null;

        // remove all flow rules of this app
        flowRuleService.removeFlowRulesById(appId);

        log.info("Stopped PortalManager {}", appId.toString());
    }

    /**
     * Request packet in via packet service.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.<DeviceId>empty());
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.<DeviceId>empty());
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.<DeviceId>empty());
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId, Optional.<DeviceId>empty());
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
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }

            IPacket iPkt = ethPkt.getPayload();
            Ip4Address srcIp;
            Ip4Address dstIp;
            if(iPkt instanceof IPv4) {
                srcIp = Ip4Address.valueOf(((IPv4) iPkt).getSourceAddress());
                dstIp = Ip4Address.valueOf(((IPv4) iPkt).getDestinationAddress());
            } else{
                log.debug("PortalManager: Payload of {} is no IPv4 packet.", ethPkt.toString());
                return;
            }

            /*  ### the packet is ipv4 ethernet type, no rules have been added for it yet,
                ### otherwise it would have been handled by these rules.
                ### Therefore the connection to the portal is checked for the source host
                ### and a redirect is done to the portal. */

            // get the portal service
            Service portalService = null;
            if(portalId != null) {
                portalService = serviceStore.getService(portalId);
                if (portalService == null) {
                    log.warn("PortalManager: No portal defined with ID {}", portalId.toString());
                    return;
                }
            } else{
                // no portal defined
                log.warn("PortalManager: Packet received. No portal defined. No rules installed.");
                return;
            }

            // install rule only if packet is not coming from the portal
            if(!portalService.getHost().ipAddresses().contains(srcIp)) {

                // install rules for all users with source ip address
                for (Host user : hostService.getHostsByIp(srcIp)) {
                    // add rules to routing devices enabling the connection between user and portal
                    Connection connection = new DefaultConnection(user, portalService);
                    // connectionStore only installs rule, if connection not already exists
                    connectionStore.addConnection(connection);
                }

                // TODO: change redirect:
                // TODO: redirect packets directly in the controller without flow rules
                // TODO: change src ip address of host, bypassing the installed portal flow rules
                // redirect if the destination of the packet differs from the portal addresses
                if (!portalService.getHost().ipAddresses().contains(dstIp)) {
                    // install redirect rules in the network device flow table

                    // no redirect with flows as requested in issue #1
                    // packetRedirectService.redirectToPortal(context, portalService.getHost());
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

    /**
     * Set the captive portal
     *
     * @param portalIp Ip address of the portal
     * @param portalPort Transport protocol port of the portal
     *
     * @return true if the portal was set correctly
     */
    @Override
    public boolean setPortal(Ip4Address portalIp, TpPort portalPort){
        checkNotNull(portalIp, "Portal IPv4 address can not be null");
        checkNotNull(portalPort, "Portal tpPort can not be null");

        // updating configuration variables
        this.portalIp = portalIp;
        this.portalPort = portalPort;

        // find hosts with portal IP address
        Set<Host> portalHosts = hostService.getHostsByIp(portalIp);
        if(portalHosts.size() == 1) {

            // create service for existing configuration
            Service portalService = new DefaultService(portalHosts.iterator().next(), portalPort, "PortalService", ProviderId.NONE);

            // update portal if the service not already exists
            if(!serviceStore.contains(portalService)) {

                serviceStore.addService(portalService);

                // remove obsolete connections of old portal
                if (portalId != null) {
                    Service oldPortalService = serviceStore.getService(portalId);
                    serviceStore.removeService(oldPortalService);
                }

                // establish new connections to portal for every host in network
                portalId = portalService.id();
                connectHostsToPortal();

                log.info("Portal is up. IP = {}, TpPort = {}, ID = {}",
                        Lists.newArrayList(portalIp.toString(), portalPort.toString(), this.portalId.toString()).toArray());
                return true;
            } else {
                // there is already a service running on portal IP and portal port
                if (portalId != null) {
                    if (portalService.equals(serviceStore.getService(portalId))) {
                        log.info("PortalManager: Portal already up on {}.",
                                portalIp.toString() + ":" + portalPort.toString());
                        return true;
                    }
                } else{
                    // the service is no portal service! otherwise the portalId had to be defined at least once!
                    log.warn("PortalManager: Another service is running on {}:{} ! Portal could not be set up.",
                            portalIp, portalPort);
                    return false;
                }
            }

        } else if(portalHosts.size() > 1){
            log.warn("PortalManager: Could not set portal. More than one host with IP = {}", portalIp);
        } else{
            log.warn("Could not set portal. No host defined with IP {}!", portalIp.toString());
        }

        return false;
    }

    /**
     * Get the Mac Address of the portal
     *
     * @return MacAddress
     */
    @Override
    public MacAddress getPortalMac() {
        Host portal = this.getPortal();
        if(portal != null){
            return portal.mac();
            } else{
            return null;
        }
    }

    /**
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    @Override
    public Set<IpAddress> getPortalIp() {
        Host portal = this.getPortal();
        if(portal != null){
            return portal.ipAddresses();
        } else{
            return null;
        }
    }

    /**
     * Get the portal as host
     *
     * @return Host
     */
    @Override
    public Host getPortal() {
        if(portalId != null){
            Service portalService = serviceStore.getService(portalId);
            if(portalService != null){
                return portalService.getHost();
            }
        }
        return null;
    }

    /**
     * Get the portal service
     *
     * @return Service
     */
    @Override
    public Service getPortalService() {
        if(portalId != null){
            return serviceStore.getService(portalId);
        }
        return null;
    }

    /**
     * Initiate a test setup
     */
    private void testSetup(){
        // just for development reasons

        Service service = null;

        Set<Host> hosts = hostService.getHostsByIp(IpAddress.valueOf("10.0.0.4"));
        if(!hosts.isEmpty()) {
            service = new DefaultService(hosts.iterator().next(), TpPort.tpPort(80), "TestService1", ProviderId.NONE);
            serviceStore.addService(service);
            hosts = null;
        }

        hosts = hostService.getHostsByIp(IpAddress.valueOf("10.0.0.4"));
        if(!hosts.isEmpty()) {
            service = new DefaultService(hosts.iterator().next(), TpPort.tpPort(22), "TestService2", ProviderId.NONE);
            serviceStore.addService(service);
        }
    }

    /**
     * Add a connection to the portal for all hosts in the network
     */
    private void connectHostsToPortal(){

        // get the portal service
        Service portalService = null;
        if(portalId != null)
            portalService = serviceStore.getService(portalId);

        hostLock.lock();

        if(portalService != null) {
            // install connection to the portal for every host in the network
            Iterable<Host> hosts = hostService.getHosts();
            for (Host host : hosts) {

                // no connection for the portal itself
                if(!portalService.getHost().equals(host)) {
                    Connection connection = new DefaultConnection(host, portalService);
                    connectionStore.addConnection(connection);
                }

            }
        }

        hostLock.unlock();
    }

    /**
     * Add a connection to the portal for a new host
     */
    private class PortalConnectionHostListener implements HostListener{

        /**
         * Reacts to the specified event.
         *
         * @param event event to be processed
         */
        @Override
        public void event(HostEvent event) {
            hostLock.lock();

            if(event.type().equals(HostEvent.Type.HOST_ADDED)){

                if(portalId == null){
                    log.warn("PortalManager: No portal defined. No rules installed.");
                    return;
                }
                // get the portal service
                Service portalService = serviceStore.getService(portalId);
                if(portalService == null){
                    log.warn("PortalManager: No portal defined with ID {}", portalId.toString());
                    return;
                }

                // only install if host is not the portal
                if(!portalService.getHost().equals(event.subject())) {
                    Connection connection = new DefaultConnection(event.subject(), portalService);
                    connectionStore.addConnection(connection);
                }
            }

            hostLock.unlock();
        }
    }

    private class InternalConfigListener implements NetworkConfigListener {

        private void reconfigureNetwork(ByodConfig cfg){
            if(cfg == null){
                return;
            }

            // check if portal is set and try to connect to new portal location
            if(cfg.portalIp() != null && cfg.portalPort() != -1){
                portalIp = cfg.portalIp();
                portalPort = TpPort.tpPort(cfg.portalPort());
                setPortal(portalIp, portalPort);
            }
            else if(cfg.portalIp() != null){
                portalIp = cfg.portalIp();
                setPortal(portalIp, portalPort);
            }
            else if(cfg.portalPort() != -1){
                portalPort = TpPort.tpPort(cfg.portalPort());
                setPortal(portalIp, portalPort);
            }
        }

        /**
         * Reacts to the specified event.
         *
         * @param event event to be processed
         */
        @Override
        public void event(NetworkConfigEvent event) {

            if(event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
                    event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED &&
                    event.configClass().equals(ByodConfig.class)){

                ByodConfig cfg = cfgService.getConfig(appId, ByodConfig.class);
                reconfigureNetwork(cfg);
                log.info("Reconfigured");
            }
        }
    }
}
