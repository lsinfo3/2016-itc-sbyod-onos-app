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

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.*;
import org.onosproject.net.Host;
import org.sardineproject.sbyod.configuration.ByodConfig;
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

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


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
    protected FlowRuleService flowRuleService;

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


    private HostListener portalConnectionHostListener;

    // make sure no new host is added to the host service, as long as
    // the hosts are iterated
    private final Lock hostLock = new ReentrantLock();

    // ID of the portal service
    ServiceId portalId = null;


    @Activate
    protected void activate() {
        appId = coreService.registerApplication(PortalService.APP_ID);

        // install drop, controller and dns rules on all devices
        // basicRuleInstaller.installRules();

        // TODO in future: Add topology listener -> adding basic rules if new device connected.

        // adding portal connection if new host was added
        portalConnectionHostListener = new PortalConnectionHostListener();
        hostService.addListener(portalConnectionHostListener);

        log.info("Started PortalManager {}", appId.toString());
    }

    @Deactivate
    protected void deactivate() {
        hostService.removeListener(portalConnectionHostListener);

        // remove all flow rules of this app
        flowRuleService.removeFlowRulesById(appId);

        log.info("Stopped PortalManager {}", appId.toString());
    }

    /**
     * Set the captive portal and update the connections of all hosts in the network
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

        // find hosts with portal IP address
        Set<Host> portalHosts = hostService.getHostsByIp(portalIp);
        if(portalHosts.size() == 1) {

            // create service for existing configuration
            Service portalService = DefaultService.builder()
                    .withIp(portalIp)
                    .withPort(portalPort)
                    .withName("PortalService")
                    .build();

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

                // update redirect service
                packetRedirectService.stopRedirect();
                packetRedirectService.activateRedirect(portalIp);

                log.debug("Portal is up. IP = {}, TpPort = {}, ID = {}",
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
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    @Override
    public Ip4Address getPortalIp() {
        if(portalId != null) {
            Service portalService = serviceStore.getService(portalId);
            return portalService.ipAddress();
        } else{
            return null;
        }
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
     * Add a connection to the portal for all hosts in the network
     */
    private void connectHostsToPortal(){

        // get the portal service
        Service portalService = null;
        if(portalId != null)
            portalService = serviceStore.getService(portalId);

        // get the default gateway host
        ByodConfig cfg = cfgService.getConfig(appId, ByodConfig.class);
        Host defaultGw = getDefaultGatewayHost(cfg.defaultGateway());

        hostLock.lock();

        if(portalService != null) {
            // install connection to the portal for every host in the network
            Iterable<Host> hosts = hostService.getHosts();
            for (Host host : hosts) {

                // no connection for the portal itself and the default gateway
                if(!host.ipAddresses().contains(portalService.ipAddress()) &&
                        ((defaultGw == null) || !defaultGw.equals(host))) {
                    Connection connection = new DefaultConnection(host, portalService);
                    connectionStore.addConnection(connection);
                    log.info("PortalManager: connectHostsToPortal() add connection of host {} to portal", host.id());
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

                // get the default gateway host
                ByodConfig cfg = cfgService.getConfig(appId, ByodConfig.class);
                Host defaultGw = getDefaultGatewayHost(cfg.defaultGateway());

                Host eventSubject = event.subject();

                // only install if host is not the portal or the default gateway
                if(!eventSubject.ipAddresses().contains(portalService.ipAddress()) &&
                        ((defaultGw == null) || !defaultGw.equals(eventSubject))) {

                    // create a new connection between the portal and the subject
                    Connection connection = new DefaultConnection(eventSubject, portalService);

                    // check if the host has obtained an IP address yet
                    if(eventSubject.ipAddresses().isEmpty()){
                        log.info("PortalManager: PortalConnectionHostListener - Portal connection for host {} " +
                                "installed. Host has no IP address.", eventSubject.id());
                        // flows are installed as soon as the host gets an ip address (Host_Update event)
                        // in class defaultConnectionStore
                    } else{
                        log.info("PortalManager: PortalConnectionHostListener - Portal connection for host {} " +
                                "installed.", eventSubject.id());
                    }
                    connectionStore.addConnection(connection);
                }
            }

            hostLock.unlock();
        }
    }

    /**
     * Returns the default gateway host or null if not found
     *
     * @param defaultGatewayIp IP address of the default gateway
     * @return Host or null if no host found
     */
    private Host getDefaultGatewayHost(Ip4Address defaultGatewayIp) {
        log.debug("PortalManager: Method getDefaultGatewayHost() called for ip={}", defaultGatewayIp);
        Set<Host> defaultGateways = hostService.getHostsByIp(defaultGatewayIp);
        log.debug("PortalManager: Method getDefaultGatewayHost() with gateway hosts={}", defaultGateways);

        if(defaultGateways.size() != 1){
            if(defaultGateways.size() == 0)
                log.debug("PortalManager: No default gateway found for IP={}.", defaultGatewayIp);
            else
                log.debug("PortalManager: More than one host with default gateway IP={} found!", defaultGatewayIp);
            return null;
        } else{
            return defaultGateways.iterator().next();
        }
    }
}
