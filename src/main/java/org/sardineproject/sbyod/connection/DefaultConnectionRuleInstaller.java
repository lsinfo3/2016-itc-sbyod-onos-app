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

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.configuration.ByodConfig;
import org.sardineproject.sbyod.service.Service;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 01.03.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class DefaultConnectionRuleInstaller implements ConnectionRuleInstaller {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final int FLOW_PRIORITY = 300;

    public static boolean MATCH_ETH_DST = false;

    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;


    @Activate
    protected void activate() {
    }

    @Deactivate
    protected void deactivate() {
    }


    /**
     * Establish a connection between the user and the service.
     * Use ConnectionStore to add a new connection.
     *
     * @param connection between user and service to install rules for
     */
    @Override
    public void addConnection(Connection connection) {

        if (connection == null) {
            log.warn("ConnectionRuleInstaller: DefaultConnection not added -> invalid parameter!");
            return;
        } else
            log.debug("ConnectionRuleInstaller: Adding connection between user with IP={} MAC={} " +
                            "and service with IP={} Port={}",
                    new String[]{connection.getUser().ipAddresses().toString(),
                            connection.getUser().mac().toString(),
                            connection.getService().ipAddress().toString(),
                            (connection.getService().tpPort() == null ? "" : connection.getService().tpPort().toString())});


        HostLocation userLocation = connection.getUser().location();
        // the device/host the service is connected to
        Host serviceHost = getConnectionServiceHost(connection);

        // check if a valid service host was found
        if (serviceHost != null) {

            // if user and service is connected to the same network device
            if (userLocation.deviceId().equals(serviceHost.location().deviceId())) {
                log.debug("ConnectionRuleInstaller: Installing flow rule only on device {}",
                        userLocation.deviceId().toString());

                // add one rule directing the traffic from user port to service port on device and vice versa
                if (userLocation.port().equals(serviceHost.location().port())) {
                    log.warn("ConnectionRuleInstaller: User {} and service with IP={} are on the same port! " +
                                    "No connection installed.",
                            connection.getUser().id(), connection.getService().ipAddress());
                } else {
                    addFlows(userLocation.port(), serviceHost.location().port(), userLocation.deviceId(),
                            serviceHost.mac(), connection);
                }

            } else {
                // get a set of all shortest paths between the connected devices
                Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                        userLocation.deviceId(), serviceHost.location().deviceId());
                if (paths.isEmpty()) {
                    log.warn("ConnectionRuleInstaller: No path found between {} and {}",
                            userLocation.toString(), serviceHost.location().toString());
                } else {
                    log.debug("ConnectionRuleInstaller: Installing connection between {} and {}",
                            userLocation.deviceId().toString(), serviceHost.location().deviceId().toString());

                    // pick one path. Under the assumption, that the path is shortest, no loops should be created.
                    Path path = paths.iterator().next();

                    // rule for first device
                    Iterator<Link> currentLinkIter = path.links().iterator();
                    Link currentLink = currentLinkIter.next();
                    addFlows(userLocation.port(), currentLink.src().port(), userLocation.deviceId(),
                            serviceHost.mac(), connection);

                    // rule for every pair of links
                    Iterator<Link> previousLinkIter = path.links().iterator();
                    while (currentLinkIter.hasNext()) {
                        Link previousLink = previousLinkIter.next();
                        currentLink = currentLinkIter.next();

                        addFlows(previousLink.dst().port(), currentLink.src().port(),
                                currentLink.src().deviceId(), serviceHost.mac(), connection);
                    }

                    // rule for last device
                    addFlows(currentLink.dst().port(), serviceHost.location().port(), serviceHost.location().deviceId(),
                            serviceHost.mac(), connection);
                }
            }
        }
    }

    /**
     * Returns the host location of the service ip address.
     * If no host in local network is found, the default gateway
     * location is returned if present.
     * Otherwise null is returned.
     *
     * @param connection A connection between a user and a service
     * @return Service host location, default gateway host location or null
     */
    private Host getConnectionServiceHost(Connection connection) {
        // service of the connection
        Service service = connection.getService();

        // get the byod config
        ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // get the ip prefix of the network
        IpPrefix ipPrefix = Ip4Prefix.valueOf(cfg.defaultGateway(), cfg.prefixLength());

        // check if ip address is in local network
        if (ipPrefix.contains(service.ipAddress())) {
            // get the host of the service ip if possible
            Set<Host> serviceHosts = hostService.getHostsByIp(service.ipAddress());
            if (serviceHosts.size() == 1) {
                // found a host connected inside the local network, try routing to him
                Host serviceHost = serviceHosts.iterator().next();
                return serviceHost;
            } else if (serviceHosts.isEmpty()) {
                log.warn("ConnectionRuleInstaller: getConnectionServiceHost() - no host found for local ip={}.",
                        service.ipAddress());
            } else {
                log.warn("ConnectionRuleInstaller: Found {} service hosts={} with ip={}. Choose unique IP address.",
                        Lists.newArrayList(
                                serviceHosts.size(),
                                serviceHosts.stream().map(Host::id).collect(Collectors.toSet()),
                                service.ipAddress())
                                .toArray());
            }
            // no host found
            return null;
        } else {
            // ip address not in local network -> send traffic to default gateway if possible
            // check if a default gateway is defined in the config
            if (cfg.defaultGateway() != null) {
                // get the default gateway host
                Set<Host> defaultGatewayHosts = hostService.getHostsByIp(cfg.defaultGateway());

                if (!defaultGatewayHosts.isEmpty()) {
                    log.info("ConnectionRuleInstaller: Using default gateway with ip={} as route for service {}",
                            cfg.defaultGateway(), connection.getService().name());
                    if (defaultGatewayHosts.size() > 1) {
                        log.info("ConnectionRuleInstaller: Found {} default gateway hosts={} with ip={}, choosing first one = {}.",
                                Lists.newArrayList(
                                        defaultGatewayHosts.size(),
                                        defaultGatewayHosts.stream().map(Host::id).collect(Collectors.toSet()),
                                        cfg.defaultGateway(),
                                        defaultGatewayHosts.iterator().next().id())
                                        .toArray());
                    }
                    Host defaultGatewayHost = defaultGatewayHosts.iterator().next();
                    // using the default gateway as service location
                    return defaultGatewayHost;
                } else {
                    // no service host and no default gateway found -> can not install connection!
                    log.warn("ConnectionRuleInstaller: No host in local network and no default gateway at {} found! " +
                                    "No connection installed for service with ip={}.",
                            cfg.defaultGateway(), connection.getService().ipAddress());
                    return null;
                }
            } else {
                // no service host and no default gateway found -> can not install connection!
                log.warn("ConnectionRuleInstaller: No host in local network and no default gateway defined! " +
                        "No connection installed for service with ip={}.", connection.getService().ipAddress());
                return null;
            }
        }
    }

    /**
     * Adds two flows to every device
     * One for the direction user -> service
     * Another for the direction service -> user
     *
     * @param userSidePort port directing towards the user
     * @param serviceSidePort port directing towards the service
     * @param forDeviceId device flow rule should be added to
     * @param connection between user and service
     */
    private void addFlows(PortNumber userSidePort, PortNumber serviceSidePort, DeviceId forDeviceId,
                          MacAddress serviceMac, Connection connection){
        addFlowUserToService(userSidePort, serviceSidePort, forDeviceId, serviceMac, connection);
        addFlowServiceToUser(serviceSidePort, userSidePort, forDeviceId, serviceMac, connection);
    }

    /**
     * Add the flow from the user to the service direction to the network device
     *
     * @param inPort The in port, where the packets are coming in
     * @param outPort The out port, where the packets are send to
     * @param forDeviceId The device id where the flow is installed
     * @param connection The connection the flows are installed for
     */
    private void addFlowUserToService(PortNumber inPort, PortNumber outPort, DeviceId forDeviceId,
                                      MacAddress serviceMac, Connection connection){

        byte protocol = connection.getService().protocol();

        // get the byod config
        ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // get the ip prefix of the network
        IpPrefix ipPrefix = Ip4Prefix.valueOf(cfg.defaultGateway(), cfg.prefixLength());

        for(IpAddress userIp : connection.getUser().ipAddresses()) {
            // only install rules for ip addresses inside the local network
            if (userIp.isIp4() && ipPrefix.contains(userIp)) {
                TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                        .matchEthType(EthType.EtherType.IPV4.ethType().toShort())
                        .matchInPort(inPort)
                        .matchIPSrc(userIp.toIpPrefix())
                        .matchEthSrc(connection.getUser().mac())
                        .matchIPDst(connection.getService().ipAddress().toIpPrefix())
                        .matchIPProtocol(protocol);

                // only match on port if it is defined
                if(connection.getService().tpPort() != null) {
                    if (protocol == IPv4.PROTOCOL_TCP) {
                        trafficSelectorBuilder.matchTcpDst(connection.getService().tpPort());
                    } else if (protocol == IPv4.PROTOCOL_UDP) {
                        trafficSelectorBuilder.matchUdpDst(connection.getService().tpPort());
                    } else {
                        log.warn("DefaultConnectionRuleInstaller: Defined internet protocol not supported!");
                        return;
                    }
                }

                // check if the match ethernet destination is set true in config
                if(MATCH_ETH_DST) {
                    trafficSelectorBuilder.matchEthDst(serviceMac);
                }

                TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                        .setOutput(outPort);

                DefaultForwardingObjective.Builder forwardingObjective = DefaultForwardingObjective.builder()
                        .withSelector(trafficSelectorBuilder.build())
                        .withTreatment(trafficTreatmentBuilder.build())
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                        .makePermanent();
                if(connection.getService().name().equals("PortalService")){
                    forwardingObjective.withPriority(FLOW_PRIORITY + 10);
                } else if(connection.getService().name().equals("Internet") ||
                        connection.getService().name().equals("Internet SSL verschluesselt")){
                    // Todo: store internet as special service
                    forwardingObjective.withPriority(FLOW_PRIORITY - 10);
                } else {
                    forwardingObjective.withPriority(FLOW_PRIORITY);
                }

                log.debug("DefaultConnectionRuleInstaller: Adding flow objective \n{} \n" +
                        "for device {} in method addFlowUserToService()", forwardingObjective, forDeviceId);
                flowObjectiveService.forward(forDeviceId, forwardingObjective.add());
                // save forwarding objective in connection
                connection.addRemoveObjective(forwardingObjective.remove(), forDeviceId);
            }
        }
    }

    /**
     * Add the flow from the service to the user direction to the network device
     *
     * @param inPort The in port, where the packets are coming in
     * @param outPort The out port, where the packets are send to
     * @param forDeviceId The device id where the flow is installed
     * @param connection The connection the flows are installed for
     */
    private void addFlowServiceToUser(PortNumber inPort, PortNumber outPort, DeviceId forDeviceId,
                                      MacAddress serviceMac, Connection connection){

        byte protocol = connection.getService().protocol();

        // get the byod config
        ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // get the ip prefix of the network
        IpPrefix ipPrefix = Ip4Prefix.valueOf(cfg.defaultGateway(), cfg.prefixLength());

        for(IpAddress userIp : connection.getUser().ipAddresses()) {
            // only install rules for ip addresses inside the local network
            if (userIp.isIp4() && ipPrefix.contains(userIp)) {

                TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                        .matchEthType(EthType.EtherType.IPV4.ethType().toShort())
                        .matchInPort(inPort)
                        .matchIPSrc(connection.getService().ipAddress().toIpPrefix())
                        .matchEthSrc(serviceMac)
                        .matchIPDst(userIp.toIpPrefix())
                        .matchIPProtocol(protocol);

                // only match on port if it is defined
                if(connection.getService().tpPort() != null) {
                    if (protocol == IPv4.PROTOCOL_TCP) {
                        trafficSelectorBuilder.matchTcpSrc(connection.getService().tpPort());
                    } else if (protocol == IPv4.PROTOCOL_UDP) {
                        trafficSelectorBuilder.matchUdpSrc(connection.getService().tpPort());
                    } else {
                        log.warn("DefaultConnectionRuleInstaller: Defined internet protocol not supported!");
                        return;
                    }
                }

                // check if the match ethernet destination is set true in config
                if (MATCH_ETH_DST) {
                    trafficSelectorBuilder.matchEthDst(connection.getUser().mac());
                }

                TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                        .setOutput(outPort);

                DefaultForwardingObjective.Builder forwardingObjective = DefaultForwardingObjective.builder()
                        .withSelector(trafficSelectorBuilder.build())
                        .withTreatment(trafficTreatmentBuilder.build())
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                        .makePermanent();
                if(connection.getService().name().equals("PortalService")){
                    forwardingObjective.withPriority(FLOW_PRIORITY + 10);
                } else if(connection.getService().name().equals("Internet") ||
                        connection.getService().name().equals("Internet SSL verschluesselt")){
                    forwardingObjective.withPriority(FLOW_PRIORITY - 10);
                }  else{
                    forwardingObjective.withPriority(FLOW_PRIORITY);
                }

                log.debug("DefaultConnectionRuleInstaller: Adding flow objective \n{} \n" +
                        "for device {} in method addFlowServiceToUser()", forwardingObjective.add(), forDeviceId);
                flowObjectiveService.forward(forDeviceId, forwardingObjective.add());
                // save forwarding objective in connection
                connection.addRemoveObjective(forwardingObjective.remove(), forDeviceId);
            }
        }
    }
}
