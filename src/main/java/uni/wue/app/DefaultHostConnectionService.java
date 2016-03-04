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

import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
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
public class DefaultHostConnectionService implements HostConnectionService {

    private static int FLOW_PRIORITY = 300;
    private static int TIMEOUT = 5*60; //seconds
    private static String APPLICATION_ID = "uni.wue.app";

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
        log.info("Started DefaultHostConnectionService");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped DefaultHostConnectionService");
    }


    /**
     * Establish a connection between the user with user IP and MAC address and
     * a service with service IP address and transport layer port
     *
     * @param userIp      the IP address of the user
     * @param userMac     the MAC address of the user
     * @param serviceIp   the IP address of the service
     * @param serviceTpPort the transport layer port of the service
     */
    @Override
    public void addConnection(Ip4Address userIp, MacAddress userMac, Ip4Address serviceIp, TpPort serviceTpPort) {

        if(userIp == null || userMac == null || serviceIp == null || serviceTpPort == null){
            log.warn("HostConnectionService: Connection not added -> invalid parameter!");
            return;
        } else
            log.info("HostConnectionService: Adding connection between user with IP={} MAC={} " +
                            "and service with IP={} Port={}",
                    new String[]{userIp.toString(), userMac.toString(), serviceIp.toString(), serviceTpPort.toString()});

        // simplifying the "addFlows" method
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("userIp", userIp);
        constraints.put("userMac", userMac);
        constraints.put("serviceIp", serviceIp);
        constraints.put("serviceTpPort", serviceTpPort);

        // get the host with corresponding IP address
        Set<Host> users = hostService.getHostsByIp(userIp);
        Set<Host> services = hostService.getHostsByIp(serviceIp);
        if(users.size() != 1 || services.size() != 1) {
            log.warn("HostConnectionService: More than one user with IP={} or service with IP={}",
                    userIp.toString(), serviceIp.toString());
            return;
        }
        HostLocation userLocation = users.iterator().next().location();
        HostLocation serviceLocation = services.iterator().next().location();

        // if user and service is connected to the same network device
        if(userLocation.deviceId().equals(serviceLocation.deviceId())){
            log.debug("HostConnectionService: Installing flow rule only on device {}",
                    userLocation.deviceId().toString());

            // add one rule directing the traffic from user port to service port on device
            addFlows(userLocation.port(), serviceLocation.port(), userLocation.deviceId(), constraints);

            return;
        } else {
            // get a path between the connected devices
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                    userLocation.deviceId(), serviceLocation.deviceId());
            if (paths.isEmpty()) {
                log.warn("HostConnectionService: No path found between {} and {}",
                        userLocation.toString(), serviceLocation.toString());
                return;
            } else {
                log.debug("HostConnectionService: Installing connection between {} and {}",
                        userLocation.deviceId().toString(), serviceLocation.deviceId().toString());
                Path path = paths.iterator().next();

                // rule for first device
                Iterator<Link> currentLinkIter = path.links().iterator();
                Link currentLink = currentLinkIter.next();
                addFlows(userLocation.port(), currentLink.src().port(), userLocation.deviceId(), constraints);

                // rule for every pair of links
                Iterator<Link> previousLinkIter = path.links().iterator();
                while(currentLinkIter.hasNext()){
                    Link previousLink = previousLinkIter.next();
                    currentLink = currentLinkIter.next();

                    addFlows(previousLink.dst().port(), currentLink.src().port(),
                            currentLink.src().deviceId(), constraints);
                }

                // rule for last device
                addFlows(currentLink.dst().port(), serviceLocation.port(), serviceLocation.deviceId(),
                        constraints);
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
     * @param constraints map containing "userIp", "userMac", "serviceIp" and "serviceTpPort"
     */
    private void addFlows(PortNumber userSidePort, PortNumber serviceSidePort, DeviceId forDeviceId,
                          Map<String, Object> constraints){
        addFlowToDevicePortalDirection(userSidePort, serviceSidePort, forDeviceId, constraints);
        addFlowToDeviceUserDirection(serviceSidePort, userSidePort, forDeviceId, constraints);
    }

    private void addFlowToDevicePortalDirection(PortNumber inPort, PortNumber outPort, DeviceId forDeviceId,
                                                Map<String, Object> constraints){

        try {
            Ip4Address userIp = (Ip4Address) constraints.get("userIp");
            MacAddress userMac = (MacAddress) constraints.get("userMac");
            Ip4Address serviceIp = (Ip4Address) constraints.get("serviceIp");
            TpPort serviceTpPort = (TpPort) constraints.get("serviceTpPort");

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchInPort(inPort)
                .matchIPSrc(userIp.toIpPrefix())
                .matchEthSrc(userMac)
                .matchIPDst(serviceIp.toIpPrefix())
                .matchTcpDst(serviceTpPort);

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(outPort);

        FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .forDevice(forDeviceId)
                .withPriority(FLOW_PRIORITY)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makeTemporary(TIMEOUT);

        flowRuleService.applyFlowRules(flowRuleBuilder.build());

        } catch (Exception e){
            log.warn("HostConnectionService: Could not add flow for user -> service direction. Missing constraint!");
            return;
        }
    }

    private void addFlowToDeviceUserDirection(PortNumber inPort, PortNumber outPort, DeviceId forDeviceId,
                                              Map<String, Object> constraints){
        try {
            Ip4Address userIp = (Ip4Address) constraints.get("userIp");
            MacAddress userMac = (MacAddress) constraints.get("userMac");
            Ip4Address serviceIp = (Ip4Address) constraints.get("serviceIp");

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchInPort(inPort)
                .matchIPSrc(serviceIp.toIpPrefix())
                .matchEthDst(userMac)
                .matchIPDst(userIp.toIpPrefix());

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(outPort);

        FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .forDevice(forDeviceId)
                .withPriority(FLOW_PRIORITY)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makeTemporary(TIMEOUT);

        flowRuleService.applyFlowRules(flowRuleBuilder.build());

        } catch (Exception e){
            log.warn("HostConnectionService: Could not add flow for service -> user direction. Missing constraint!");
            return;
        }
    }
}
