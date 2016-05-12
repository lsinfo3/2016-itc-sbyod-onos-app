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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uni.wue.app.connection.Connection;
import uni.wue.app.connection.ConnectionStore;
import uni.wue.app.connection.DefaultConnection;
import uni.wue.app.redirect.PacketRedirectService;
import uni.wue.app.service.DefaultService;
import uni.wue.app.service.ServiceId;
import uni.wue.app.service.ServiceStore;
import uni.wue.app.service.Service;
//import org.osgi.service.component.ComponentContext;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


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
                    "byod"){
                @Override
                public ByodConfig createConfig(){
                    return new ByodConfig();
                }
            }
    );

    // Portal configuration values
    // Hardcoded values are default values.

    private Ip4Address portalIp = Ip4Address.valueOf("10.0.0.3");

    private TpPort portalPort = TpPort.tpPort(80);

    // make sure no new host is added to the host service, as long as
    // the hosts are iterated
    private final Lock hostLock = new ReentrantLock();

    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    // ID of the portal service
    ServiceId portalId = null;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication("uni.wue.app");

        cfgService.addListener(cfgListener);
        factories.forEach(cfgService::registerConfigFactory);
        cfgListener.reconfigureNetwork(cfgService.getConfig(appId, ByodConfig.class));

        basicRuleInstaller.installRules();
        // TODO: Add topology listener -> adding basic rules if new device connected.
        packetService.addProcessor(processor, PacketProcessor.director(2));

        //testSetup();

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
            Service portalService = null;
            if(portalId != null) {
                portalService = serviceStore.getService(portalId);
                if (portalService == null) {
                    log.warn("PortalManager: No portal defined with ID {}", portalId.toString());
                    return;
                }
            } else{
                // no portal defined
                log.debug("PortalManager: No portal defined.");
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

            // install rule only if packet is not coming from the portal
            if(!portalService.getHost().ipAddresses().contains(srcIp)) {

                // install rules for all users with source ip address
                for (Host user : hostService.getHostsByIp(srcIp)) {
                    // add rules to routing devices enabling the connection between user and portal
                    Connection connection = new DefaultConnection(user, portalService);
                    connectionStore.addConnection(connection);
                }

                // redirect if the destination of the packet differs from the portal addresses
                if (!portalService.getHost().ipAddresses().contains(dstIp)) {
                    // install redirect rules in the network device flow table
                    packetRedirectService.redirectToPortal(context, portalService.getHost());
                }

                // send context to flow table, where it should be handled
                //context.treatmentBuilder().setOutput(PortNumber.TABLE);
                //context.send();
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

    /**
     * Set the captive portal
     *
     * @param portalIp Ip address of the portal
     * @param portalTpPort Transport protocol port of the portal
     *
     * @return true if the portal was set correctly
     */
    @Override
    public boolean setPortal(Ip4Address portalIp, TpPort portalTpPort){
        checkNotNull(portalIp, "Portal IPv4 address can not be null");

        // find hosts with portal IP address
        Set<Host> portalHosts = hostService.getHostsByIp(portalIp);
        if(portalHosts.size() == 1) {

            Service portalService = new DefaultService(portalHosts.iterator().next(), portalTpPort, "PortalService", ProviderId.NONE);
            // update portal if the service not already exists
            if(serviceStore.addService(portalService)) {

                // remove old connections
                if (portalId != null) {
                    Service oldPortalService = serviceStore.getService(portalId);
                    serviceStore.removeService(oldPortalService);
                }

                // establish new connections to portal
                portalId = portalService.id();
                connectHostsToPortal();

                log.info("Portal is up. IP = {}, TpPort = {}, ID = {}",
                        Lists.newArrayList(portalIp.toString(), portalTpPort.toString(), this.portalId.toString()).toArray());
                return true;
            } else if(portalId != null) {
                if (portalService.equals(serviceStore.getService(portalId))) {
                    log.info("PortalManager: Portal already up on {}.",
                            portalIp.toString() + ":" + portalTpPort.toString());
                    return true;
                }
            }

            log.warn("Could not set portal. Another service is already active on '{}' !",
                    portalIp.toString() + ":" + portalTpPort.toString());
            return false;

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
                    log.debug("PortalManager: No portal defined.");
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
            if(cfg.portalIp() != null){
                portalIp = cfg.portalIp();
            }
            if(cfg.portalPort() != null){
                portalPort = cfg.portalPort();
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
