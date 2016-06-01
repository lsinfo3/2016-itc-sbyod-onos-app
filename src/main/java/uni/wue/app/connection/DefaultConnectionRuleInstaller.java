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
package uni.wue.app.connection;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.EthType;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 01.03.16.
 */
@Component(immediate = true)
@Service
public class DefaultConnectionRuleInstaller implements ConnectionRuleInstaller {

    private static final int FLOW_PRIORITY = 300;
    private static final String APPLICATION_ID = "uni.wue.app";

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;


    @Activate
    protected void activate() {
        //log.info("Started DefaultConnectionRuleInstaller");
    }

    @Deactivate
    protected void deactivate() {
        //log.info("Stopped DefaultConnectionRuleInstaller");
    }


    /**
     * Establish a connection between the user and the service.
     * Use ConnectionStore to add a new connection.
     *
     * @param connection
     */
    @Override
    public void addConnection(Connection connection) {

        if(connection == null){
            log.warn("ConnectionRuleInstaller: DefaultConnection not added -> invalid parameter!");
            return;
        } else
            log.debug("ConnectionRuleInstaller: Adding connection between user with IP={} MAC={} " +
                            "and service with IP={} Port={}",
                    new String[]{connection.getUser().ipAddresses().toString(),
                            connection.getUser().mac().toString(),
                            connection.getService().getHost().ipAddresses().toString(),
                            connection.getService().getTpPort().toString()});


        HostLocation userLocation = connection.getUser().location();
        HostLocation serviceLocation = connection.getService().getHost().location();

        // if user and service is connected to the same network device
        if(userLocation.deviceId().equals(serviceLocation.deviceId())){
            log.debug("ConnectionRuleInstaller: Installing flow rule only on device {}",
                    userLocation.deviceId().toString());

            // add one rule directing the traffic from user port to service port on device
            addFlows(userLocation.port(), serviceLocation.port(), userLocation.deviceId(), connection);

            return;
        } else {
            // get a path between the connected devices
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                    userLocation.deviceId(), serviceLocation.deviceId());
            if (paths.isEmpty()) {
                log.warn("ConnectionRuleInstaller: No path found between {} and {}",
                        userLocation.toString(), serviceLocation.toString());
                return;
            } else {
                log.debug("ConnectionRuleInstaller: Installing connection between {} and {}",
                        userLocation.deviceId().toString(), serviceLocation.deviceId().toString());
                Path path = paths.iterator().next();

                // rule for first device
                Iterator<Link> currentLinkIter = path.links().iterator();
                Link currentLink = currentLinkIter.next();
                addFlows(userLocation.port(), currentLink.src().port(), userLocation.deviceId(), connection);

                // rule for every pair of links
                Iterator<Link> previousLinkIter = path.links().iterator();
                while(currentLinkIter.hasNext()){
                    Link previousLink = previousLinkIter.next();
                    currentLink = currentLinkIter.next();

                    addFlows(previousLink.dst().port(), currentLink.src().port(),
                            currentLink.src().deviceId(), connection);
                }

                // rule for last device
                addFlows(currentLink.dst().port(), serviceLocation.port(), serviceLocation.deviceId(),
                        connection);
            }
        }

        return;
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
                          Connection connection){
        addFlowUserToService(userSidePort, serviceSidePort, forDeviceId, connection);
        addFlowServiceToUser(serviceSidePort, userSidePort, forDeviceId, connection);
    }

    private void addFlowUserToService(PortNumber inPort, PortNumber outPort, DeviceId forDeviceId,
                                      Connection connection){

        for(IpAddress userIp : connection.getUser().ipAddresses())
            for(IpAddress serviceIp : connection.getService().getHost().ipAddresses())
                if(userIp.isIp4() && serviceIp.isIp4()) {
                    TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                            .matchEthType(EthType.EtherType.IPV4.ethType().toShort())
                            .matchInPort(inPort)
                            .matchIPSrc(userIp.toIpPrefix())
                            .matchEthSrc(connection.getUser().mac())
                            .matchIPDst(serviceIp.toIpPrefix())
                            .matchIPProtocol(IPv4.PROTOCOL_TCP)
                            .matchTcpDst(connection.getService().getTpPort());

                    TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                            .setOutput(outPort);

                    FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder()
                            .withSelector(trafficSelectorBuilder.build())
                            .withTreatment(trafficTreatmentBuilder.build())
                            .forDevice(forDeviceId)
                            .withPriority(FLOW_PRIORITY)
                            .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                            .forTable(100)
                            .makePermanent();

                    FlowRule flowRule = flowRuleBuilder.build();
                    flowRuleService.applyFlowRules(flowRule);
                    // save flow rule in connection
                    connection.addFlowRule(flowRule);
                }
    }

    private void addFlowServiceToUser(PortNumber inPort, PortNumber outPort, DeviceId forDeviceId,
                                      Connection connection){

        for(IpAddress userIp : connection.getUser().ipAddresses())
            for(IpAddress serviceIp : connection.getService().getHost().ipAddresses())
                if(userIp.isIp4() && serviceIp.isIp4()) {
                    TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                            .matchEthType(EthType.EtherType.IPV4.ethType().toShort())
                            .matchInPort(inPort)
                            .matchIPSrc(serviceIp.toIpPrefix())
                            .matchEthDst(connection.getUser().mac())
                            .matchIPDst(userIp.toIpPrefix());

                    TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                            .setOutput(outPort);

                    FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder()
                            .withSelector(trafficSelectorBuilder.build())
                            .withTreatment(trafficTreatmentBuilder.build())
                            .forDevice(forDeviceId)
                            .withPriority(FLOW_PRIORITY)
                            .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                            .forTable(100)
                            .makePermanent();

                    FlowRule flowRule = flowRuleBuilder.build();
                    flowRuleService.applyFlowRules(flowRule);
                    // save flow rule in connection
                    connection.addFlowRule(flowRule);
                }
    }
}
