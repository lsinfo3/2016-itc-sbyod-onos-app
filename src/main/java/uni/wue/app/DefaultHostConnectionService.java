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

import java.util.List;
import java.util.Set;

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
     * Establish a connection between the user with userIp and userMac and a service with serviceIp and servicePort
     *
     * @param userIp      the IP address of the user
     * @param userMac     the MAC address of the user
     * @param serviceIp   the IP address of the service
     * @param servicePort the port of the service
     */
    @Override
    public void addConnection(Ip4Address userIp, MacAddress userMac, Ip4Address serviceIp, TpPort servicePort) {

        if(userIp == null || userMac == null || serviceIp == null || servicePort == null){
            log.warn("HostConnectionService: Connection not added -> invalid parameter!");
            return;
        } else
            log.info("HostConnectionService: Adding connection between user with IP={} MAC={} " +
                            "and service with IP={} Port={}",
                    new String[]{userIp.toString(), userMac.toString(), serviceIp.toString(), servicePort.toString()});

        Set<Host> users = hostService.getHostsByIp(userIp);
        Set<Host> services = hostService.getHostsByIp(serviceIp);
        if(users.size() != 1 || services.size() != 1) {
            log.warn("HostConnectionService: More than one user with IP={} or service with IP={}",
                    userIp.toString(), serviceIp.toString());
            return;
        }

        HostLocation userLocation = users.iterator().next().location();
        HostLocation serviceLocation = services.iterator().next().location();
        // FIXME: How does ONOS find a path, if there is no connection?
        Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                userLocation.deviceId(), serviceLocation.deviceId());
        if(paths.isEmpty()) {
            log.warn("HostConnectionService: No path found between {} and {}",
                    userLocation.toString(), serviceLocation.toString());
            return;
        }

        Path path = paths.iterator().next();
        for(Link link : path.links()){
            // for every link, add a flow rule to the source of the link,
            // routing the packet to the port of destination of the link
            addFlowToDevice(link.src().port(), link.src().deviceId(), userIp, userMac, serviceIp, servicePort);
        }

        // for the last device, add the rule directing the traffic to the service device port
        // TODO: check if the port and device is correct
        addFlowToDevice(path.dst().port(), path.dst().deviceId(), userIp, userMac, serviceIp, servicePort);
    }

    private void addFlowToDevice(PortNumber port, DeviceId deviceId, Ip4Address userIp, MacAddress userMac,
                                 Ip4Address serviceIp, TpPort servicePort) {

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchIPSrc(userIp.toIpPrefix())
                .matchEthSrc(userMac)
                .matchIPDst(serviceIp.toIpPrefix())
                .matchTcpDst(servicePort);

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(port);

        FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .forDevice(deviceId)
                .withPriority(FLOW_PRIORITY)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makeTemporary(TIMEOUT);

        flowRuleService.applyFlowRules(flowRuleBuilder.build());
    }
}
