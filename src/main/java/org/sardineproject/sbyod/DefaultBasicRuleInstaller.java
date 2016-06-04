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
import org.onosproject.net.Device;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by lorry on 03.03.16.
 */
@Component(immediate = true)
@Service
public class DefaultBasicRuleInstaller implements BasicRuleInstaller {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final int DROP_RULE_PRIORITY = 100;
    private static final int CONTROLLER_RULE_PRIORITY = 200;
    // the port were the portal is accessible on
    private static final TpPort PORTAL_TCP_PORT = TpPort.tpPort(80);
    // defines the table number
    private static final int FLOW_TABLE = 100;

    // decides if a rule to drop any traffic not fitting the
    // 'send to controller rule' is installed or not
    private static final boolean ADD_DROP_RULE = false;

    private final Lock lock = new ReentrantLock();
    private final Condition ruleAdded = lock.newCondition();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    /**
     * Install two rules on every network device:
     * First rule:  drops everything
     * Second rule: sends packet to controller
     */
    public void installRules() {

        Iterable<Device> devices = deviceService.getDevices();

        if(ADD_DROP_RULE) {
            for(Device device : devices){

                // get flow rule to drop packets
                FlowRule.Builder dropRuleBuilder = getDropRuleBuilder();
                FlowRule rule = dropRuleBuilder.forDevice(device.id()).build();

                flowRuleService.applyFlowRules(rule);
            }
        }


        for(Device device : devices){

            // get flow rule sending packet to controller
            FlowRule.Builder controllerRuleBuilder = getControllerRuleBuilder();
            FlowRule rule = controllerRuleBuilder.forDevice(device.id()).build();

            flowRuleService.applyFlowRules(rule);

        }

    }

    /**
     * Generate a permanent rule to drop every packet with priority of 100
     *
     * @return a flow rule builder for the dropping rule
     */
    private FlowRule.Builder getDropRuleBuilder(){

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.ALL);

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder().drop();

        FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder();
        flowRuleBuilder.withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .withPriority(DROP_RULE_PRIORITY)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .forTable(FLOW_TABLE)
                .makePermanent();

        return flowRuleBuilder;
    }

    /**
     * Generate a permanent rule to send packets with IPv4 type
     * and priority of 200 to the controller
     *
     * @return a flow rule builder for the controller rule
     */
    private FlowRule.Builder getControllerRuleBuilder() {

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.IPV4.ethType().toShort());

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.CONTROLLER);

        FlowRule.Builder flowRuleBuilder = DefaultFlowRule.builder();
        flowRuleBuilder.withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .withPriority(CONTROLLER_RULE_PRIORITY)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .forTable(FLOW_TABLE)
                .makePermanent();

        return flowRuleBuilder;
    }

}
