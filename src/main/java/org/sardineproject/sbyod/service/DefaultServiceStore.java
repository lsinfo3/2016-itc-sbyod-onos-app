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
package org.sardineproject.sbyod.service;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.TpPort;
import org.onosproject.codec.CodecService;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.store.service.StorageService;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.configuration.ByodConfig;
import org.slf4j.Logger;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 10.03.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class DefaultServiceStore implements ServiceStore {

    private static final Logger log = getLogger(PortalManager.class);
    private static String APPLICATION_ID = PortalService.APP_ID;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CodecService codecService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostArpService hostArpService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;


    // TODO: use distributed set (problem with kryo)

    private Set<org.sardineproject.sbyod.service.Service> services;

    // TODO: remove service if host where the service is running on has disconnected!

    @Activate
    protected void activate(){
        // empty service set
        services = new HashSet<>();

        codecService.registerCodec(org.sardineproject.sbyod.service.Service.class, new ServiceCodec());
    }

    @Deactivate
    protected void deactivate(){
        services.clear();
    }


    /**
     * Add a new service to the store
     *
     * @param service service to add
     * @return true service was added to collection
     *          false service is null or already in collection
     */
    @Override
    public boolean addService(org.sardineproject.sbyod.service.Service service) {
        if(service == null){
            log.warn("ServiceStore - addService(service): service can not be null!");
            return false;
        }
        // only add service if it is not already in collection
        if(!services.contains(service)) {
            // check if the service host is known to onos
            checkServiceHost(service);
            log.debug("ServiceStore: Added service {}", service.toString());
            return services.add(service);
        } else {
            log.debug("ServiceStore: Could not add service {}. Service already active.", service.toString());
            return false;
        }
    }

    private void checkServiceHost(Service service){
        // get the sbyod config
        ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // get the ip prefix of the network
        IpPrefix ipPrefix = Ip4Prefix.valueOf(cfg.defaultGateway(), cfg.prefixLength());

        // check if ip is in network
        if(ipPrefix.contains(service.ipAddress())) {
            // check if service host is already present in network
            if(hostService.getHostsByIp(service.ipAddress()).isEmpty()) {
                log.warn("ServiceStore: addService()\nNo host found with IP={}. Sending Arp/Ndp request.",
                        service.ipAddress());
                // send arp to discover
                hostArpService.sendRequest(service.ipAddress());
            }
        } else {
            // check if default gateway is in network
            if(cfg.defaultGateway() != null && hostService.getHostsByIp(cfg.defaultGateway()).isEmpty()){
                log.warn("ServiceStore: addService()\nNo host found with IP={}. Sending Arp/Ndp request.",
                        cfg.defaultGateway());
                // send arp to discover
                hostArpService.sendRequest(cfg.defaultGateway());
            }

        }
    }

    /**
     * Removes a service from the store and all of the connections with this service.
     *
     * @param service service to remove
     * @return true if service was removed,
     *          false if no such service in collection
     */
    @Override
    public boolean removeService(org.sardineproject.sbyod.service.Service service) {
        if(service == null){
            log.warn("ServiceStore - removeService(service): service can not be null!");
            return false;
        }

        if(this.contains(service)) {
            // get the connections of the service
            Set<Connection> serviceConnections = connectionStore.getConnections(service);
            // remove all connections
            serviceConnections.forEach(c -> connectionStore.removeConnection(c));
            // remove the service
            services.remove(service);
            return true;
        } else{
            return false;
        }
    }

    /**
     * Get all services from the store
     *
     * @return Set of service
     */
    @Override
    public Set<org.sardineproject.sbyod.service.Service> getServices() {
        return services.stream().collect(Collectors.toSet());
    }

    /**
     * Get the service with serviceId
     *
     * @param serviceId ID of the service
     * @return service
     */
    @Override
    public org.sardineproject.sbyod.service.Service getService(ServiceId serviceId) {
        if(serviceId == null){
            log.warn("ServiceStore - getService(ServiceId): ServiceId can not be null!");
            return null;
        }

        // get the service with ServiceId
        Set<org.sardineproject.sbyod.service.Service> result = services.stream()
                .filter(s -> s.id().equals(serviceId))
                .collect(Collectors.toSet());

        if(result.size() == 0){
            log.debug("ServiceStore: No service found with serviceId = {}", serviceId.toString());
            return null;
        }
        else if(result.size() > 1){
            log.warn("ServiceStore: More than one service with serviceId = {}", serviceId.toString());
            return null;
        }
        return result.iterator().next();
    }

    /**
     * Get the services with IP address
     *
     * @param ip4Address of the service
     * @return Set of services
     */
    @Override
    public Set<org.sardineproject.sbyod.service.Service> getService(Ip4Address ip4Address) {
        return services.stream()
                .filter(s -> s.ipAddress().equals(ip4Address))
                .collect(Collectors.toSet());
    }

    /**
     * Get the services with transport protocol port
     *
     * @param tpPort of the service
     * @return Set of services
     */
    @Override
    public Set<org.sardineproject.sbyod.service.Service> getService(TpPort tpPort) {
        return services.stream()
                .filter(s -> s.tpPort().equals(tpPort))
                .collect(Collectors.toSet());
    }

    /**
     * Get services by name
     *
     * @param name of the service
     * @return set of services
     */
    @Override
    public Set<org.sardineproject.sbyod.service.Service> getService(String name) {
        return services.stream()
                .filter(s -> s.name().equals(name))
                .collect(Collectors.toSet());
    }

    /**
     * ServiceStore already containing the service
     *
     * @param service to check
     * @return true if service is in store
     */
    @Override
    public Boolean contains(org.sardineproject.sbyod.service.Service service) {
        return services.contains(service);
    }


}
