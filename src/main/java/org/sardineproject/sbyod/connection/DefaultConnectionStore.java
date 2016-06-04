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
package org.sardineproject.sbyod.connection;

import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.codec.CodecService;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.Host;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.store.service.StorageService;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.PortalService;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
@Component(immediate = true)
@Service
public class DefaultConnectionStore implements ConnectionStore {

    private static final Logger log = getLogger(PortalManager.class);
    private static String APPLICATION_ID = PortalService.APP_ID;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionRuleInstaller connectionRuleInstaller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CodecService codecService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    private final HostListener connectionHostListener = new ConnectionHostListener();
    //private final FlowRuleListener removedFlowRuleListener = new RemovedFlowRuleListener();

    // if a connection is removed, the RemovedFlowRule listener should wait
    // until the connection is deleted
    private final Lock flowRuleLock = new ReentrantLock();

    // TODO: use distributed set (problem with kryo)
    //private DistributedSet<Connection> connections;
    private Set<Connection> connections;

    /*Serializer serializer = Serializer.using(KryoNamespaces.API.newBuilder()
            .nextId(KryoNamespaces.BEGIN_USER_CUSTOM_ID)
            .register(DefaultConnection.class)
            .register(org.onlab.packet.Ip4Address.class)
            .register(byte[].class)
            .register(org.onlab.packet.IpAddress.Version.class)
            .register(org.onlab.packet.TpPort.class)
            .register(org.onlab.packet.MacAddress.class)
            .build());*/

    @Activate
    protected void activate(){
        /*connections = storageService.<Connection>setBuilder()
                .withApplicationId(applicationIdStore.getAppId(APPLICATION_ID))
                .withSerializer(serializer)
                .withName("connections")
                .build();
        connections.clear();*/

        connections = new HashSet<>();

        codecService.registerCodec(Connection.class, new ConnectionCodec());

        // add listener to detect host moved, updated or removed
        hostService.addListener(connectionHostListener);

        //log.info("Started ConnectionStore");
    }

    @Deactivate
    protected void deactivate(){
        hostService.removeListener(connectionHostListener);
        // remove all connections
        connections.clear();
        // flow rules are removed in PortalManager class

        //log.info("Stopped ConnectionStore");
    }

    /**
     * Add a new connection to the service
     *
     * @param connection connection to add
     */
    @Override
    public void addConnection(Connection connection) {
        if(!connections.contains(connection)) {

            connectionRuleInstaller.addConnection(connection);
            connections.add(connection);
            log.debug("Added connection {}", connection);
        } else{
            log.debug("ConnectionStore: Connection already installed. Nothing done. Connection = {}", connection);
        }
    }

    /**
     * Removes the connection between user and service
     *
     * @param connection connection to remove
     */
    @Override
    public void removeConnection(Connection connection) {
        // first remove the connection from the store
        // as the event triggered by the flow rule removal
        // would remove it again
        connections.remove(connection);

        // remove the flow rules on the network devices
        Set<FlowRule> flowRules = connection.getFlowRules();
        for(FlowRule flowRule : flowRules){
            flowRuleService.removeFlowRules(flowRule);
        }
    }

    /**
     * Get the set of connection for user with IP address
     *
     * @param userIp IPv4 address of the user
     * @return set of connections
     */
    @Override
    public Set<Connection> getUserConnections(Ip4Address userIp) {
        return connections.stream()
                .filter(c -> c.getUser().ipAddresses().contains(userIp))
                .collect(Collectors.toSet());
    }

    /**
     * Get the set of connections for source IP and source MAC
     *
     * @param userIp user IP
     * @param userMac user Mac
     * @return set of connections
     */
    @Override
    public Set<Connection> getUserConnections(Ip4Address userIp, MacAddress userMac) {
        return connections.stream()
                .filter(c -> (c.getUser().ipAddresses().contains(userIp) && c.getUser().mac().equals(userMac)))
                .collect(Collectors.toSet());
    }

    // TODO: Remove! -> better use getConnections(Service)
    /**
     * Get the set of connections for destination IP and destination traffic protocol port
     *
     * @param dstIp destination IP
     * @param dstTpPort destination transport protocol port
     * @return set of connections
     */
    @Override
    public Set<Connection> getServiceConnections(Ip4Address dstIp, TpPort dstTpPort) {
        return connections.stream()
                .filter(c -> (c.getService().getHost().ipAddresses().contains(dstIp)
                        && c.getService().getTpPort().equals(dstTpPort)))
                .collect(Collectors.toSet());
    }

    /**
     * Get the connection between the user and the service
     *
     * @param user user
     * @param service service
     * @return connection between user and service
     */
    @Override
    public Connection getConnection(Host user, org.sardineproject.sbyod.service.Service service) {
        Set<Connection> result = connections.stream()
                .filter(c -> c.getUser().equals(user) && c.getService().equals(service))
                .collect(Collectors.toSet());
        if(result.size() == 1)
            return result.iterator().next();
        else
            return null;
    }

    /**
     * Get the connections of a service
     *
     * @param service service
     * @return Set of connections
     */
    @Override
    public Set<Connection> getConnections(org.sardineproject.sbyod.service.Service service) {
        return connections.stream()
                .filter(c -> c.getService().equals(service))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Connection> getConnections(){
        return connections.stream()
                .collect(Collectors.toSet());
    }

    /**
     * Ask if connection is already installed
     *
     * @param connection the connection to check
     * @return true if connection already installed
     */
    @Override
    public Boolean contains(Connection connection) {
        return connections.contains(connection);
    }


    private class ConnectionHostListener implements HostListener{

        /**
         * Reacts to the specified event.
         *
         * @param event event to be processed
         */
        @Override
        public void event(HostEvent event) {
            switch(event.type()){
                case HOST_REMOVED:
                    removeHostConnections(event);
                    break;
                case HOST_MOVED:
                    updateHostConnections(event);
                    break;
                case HOST_UPDATED:
                    updateHostConnections(event);
                    break;
            }
        }

        // remove all connections of the host
        private void removeHostConnections(HostEvent event){
            Host host = event.subject();
            for(IpAddress ipAddress : host.ipAddresses()) {
                if(ipAddress.isIp4()) {
                    Set<Connection> connections = getUserConnections(ipAddress.getIp4Address());
                    for(Connection connection : connections){
                        removeConnection(connection);
                    }
                }
            }
        }

        // update the connections of the host
        private void updateHostConnections(HostEvent event){
            Host host = event.subject();
            for(IpAddress ipAddress : host.ipAddresses()){
                if(ipAddress.isIp4()){
                    Set<Connection> connections = getUserConnections(ipAddress.getIp4Address());
                    for(Connection connection : connections){
                        // removes the old flow rules from the devices
                        removeConnection(connection);
                        // installs new flow rules depending on the new host location
                        addConnection(connection);
                    }
                }
            }
        }
    }

    // TODO: Throws NullPointerException even if not active???
    private class RemovedFlowRuleListener implements FlowRuleListener{

        /**
         * Indicates whether the specified event is of interest or not.
         * Default implementation always returns true.
         *
         * @param event event to be inspected
         * @return true if event is relevant; false otherwise
         */
        @Override
        public boolean isRelevant(FlowRuleEvent event) {

            // events of rule removal and with this app id
            if(event.type().equals(FlowRuleEvent.Type.RULE_REMOVED))
                if(event.subject().appId() == applicationIdStore.getAppId(APPLICATION_ID).id()){
                    return true;
                }

            return false;
        }

        /**
         * Reacts to the specified event.
         *
         * @param event event to be processed
         */
        @Override
        public void event(FlowRuleEvent event) {
            FlowRule flowRule = event.subject();

            // collect all connections that depend on this flow rule
            Set<Connection> relevantConnections = connections.stream()
                    .filter(c -> c.getFlowRules().contains(flowRule))
                    .collect(Collectors.toSet());

            // remove all connections depending on the flow rule
            relevantConnections.forEach(c -> removeConnection(c));
        }
    }
}
