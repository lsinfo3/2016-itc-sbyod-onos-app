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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 03.03.16.
 */
@Component(immediate = true)
@Service
public class DefaultBasicRuleInstaller implements BasicRuleInstaller {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final int DROP_RULE_PRIORITY = 100;
    private static final int CONTROLLER_RULE_PRIORITY = 200;
    private static final int DNS_RULE_PRIORITY = 200;
    // the port were the portal is accessible on
    private static final TpPort PORTAL_TCP_PORT = TpPort.tpPort(80);
    // defines the table number
    private static final int FLOW_TABLE = 100;

    // decides if a rule to drop any traffic not fitting the
    // 'send to controller rule' is installed or not
    private static final boolean ADD_DROP_RULE = false;

    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;


    /**
     * Install two rules on every network device:
     * First rule:  drops everything
     * Second rule: sends packet to controller
     */
    public void installRules() {

        Iterable<Device> devices = deviceService.getDevices();

        for(Device device : devices){

            // add default drop rule
            if(ADD_DROP_RULE) {
                ForwardingObjective forwardingObjective = getDropRuleObjective();
                log.debug("DefaultBasicRuleInstaller: Adding flow objective \n{} \n" +
                        "for device {} in method installRules()", forwardingObjective, device.id());
                flowObjectiveService.forward(device.id(), forwardingObjective);
            }

            // add controller rule
            ForwardingObjective forwardingObjective = getControllerObjective();
            log.debug("DefaultBasicRuleInstaller: Adding flow objective \n{} \n" +
                    "for device {} in method installRules()", forwardingObjective, device.id());
            flowObjectiveService.forward(device.id(), forwardingObjective);

        }
    }

    /**
     * Generate a permanent objective to drop every packet not matching any other rule
     *
     * @return Forwarding objective for the dropping rule
     */
    private ForwardingObjective getDropRuleObjective(){

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.ALL);

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder().drop();

        return DefaultForwardingObjective.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .withPriority(DROP_RULE_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makePermanent()
                .add();
    }

    /**
     * Generate a permanent objective to send packets with IPv4 type
     * and priority of 200 to the controller
     *
     * @return Forwarding objective for the controller rule
     */
    private ForwardingObjective getControllerObjective() {

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.IPV4.ethType().toShort());

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.CONTROLLER);

        return DefaultForwardingObjective.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .withPriority(CONTROLLER_RULE_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makePermanent()
                .add();
    }

}
