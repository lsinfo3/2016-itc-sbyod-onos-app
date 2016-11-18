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

import com.google.common.collect.Multimap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Deactivate;
import org.onosproject.codec.CodecService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.sardineproject.sbyod.portal.PortalManager;
import org.sardineproject.sbyod.portal.PortalService;
import org.sardineproject.sbyod.service.Service;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class DefaultConnectionStore implements ConnectionStore {

    private static final Logger log = getLogger(PortalManager.class);
    private static String APPLICATION_ID = PortalService.APP_ID;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionRuleInstaller connectionRuleInstaller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CodecService codecService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;


    private HostListener connectionHostListener;
    //private final FlowRuleListener removedFlowRuleListener = new RemovedFlowRuleListener();

    // if a connection is removed, the RemovedFlowRule listener should wait
    // until the connection is deleted
    private final Lock flowRuleLock = new ReentrantLock();

    // TODO: use distributed set (problem with kryo)
    //private DistributedSet<Connection> connections;
    private Set<Connection> connections;

    @Activate
    protected void activate(){

        connections = new HashSet<>();

        codecService.registerCodec(Connection.class, new ConnectionCodec());

        // add listener to detect host moved, updated or removed
        connectionHostListener = new ConnectionHostListener();
        hostService.addListener(connectionHostListener);
    }

    @Deactivate
    protected void deactivate(){
        hostService.removeListener(connectionHostListener);
        // remove all connections
        connections.clear();
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
            log.debug("ConnectionStore: Added connection {}", connection);
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

        // todo: also reset flow objectives of the connection if it is not completely deleted?

        // remove the flow objectives on the network devices
        Multimap<ForwardingObjective, DeviceId> forwardingObjectives = connection.getForwardingObjectives();

        for(ForwardingObjective fo : forwardingObjectives.keySet()){

            // get the device id where the objective is installed on
            Collection<DeviceId> deviceIds = forwardingObjectives.get(fo);

            log.debug("DefaultConnectionStore: Removing flow objective \n{} \n" +
                    "for device {} in method removeConnection()", fo, deviceIds);
            // removing objective for every device
            for(DeviceId deviceId : deviceIds){
                flowObjectiveService.forward(deviceId, fo);
            }
        }
    }

    /**
     * Get the connection between the user and the service
     *
     * @param user user
     * @param service service
     * @return connection between user and service
     */
    @Override
    public Connection getConnection(Host user, Service service) {
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
    public Set<Connection> getConnections(Service service) {
        return connections.stream()
                .filter(c -> c.getService().equals(service))
                .collect(Collectors.toSet());
    }

    /**
     * Get all connections of a host defined by the id
     *
     * @param host Host user of the connection
     * @return Set of connections
     */
    @Override
    public Set<Connection> getConnections(Host host) {
        return connections.stream()
                .filter(c -> c.getUser().id().equals(host.id()))
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
                    log.info("ConnectionStore: Host removed -> Removed host connections for host {}", event.subject().id());
                    break;
                case HOST_MOVED:
                    updateHostConnections(event);
                    log.info("ConnectionStore: Host moved -> Updated host connections for host {}", event.subject().id());
                    break;
                case HOST_UPDATED:
                    updateHostConnections(event);
                    log.info("ConnectionStore: Host updated -> Updated host connections for host {}", event.subject().id());
                    break;
            }
        }

        // remove and reset all connections of the host
        private void removeHostConnections(HostEvent event){
            Host eventSubject = event.subject();
            Set<Connection> subjectConnections = getConnections(eventSubject);
            log.info("DefaultConnectionStore: ConnectionHostListener event. Removed connections of host {} to services: {}",
                    eventSubject, subjectConnections.stream().map(Connection::getService).collect(Collectors.toSet()));
            for(Connection connection : subjectConnections){
                removeConnection(connection);
            }
        }

        // update the connections of the host
        private void updateHostConnections(HostEvent event){
            Host eventSubject = event.subject();
            Set<Connection> subjectConnections = getConnections(eventSubject);
            log.info("DefaultConnectionStore: ConnectionHostListener event. Update connections of host {} to services: {}",
                    eventSubject, subjectConnections.stream().map(Connection::getService).collect(Collectors.toSet()));
            for(Connection connection : subjectConnections){
                // removes the old flow rules from the devices
                removeConnection(connection);
                // installs new flow rules depending on the new host object
                addConnection(new DefaultConnection(eventSubject, connection.getService()));
            }
        }
    }
}
