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

import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.sardineproject.sbyod.portal.PortalManager;
import org.sardineproject.sbyod.portal.PortalService;
import org.slf4j.Logger;

import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 03.03.16.
 * Component realizing the table-miss action drop all functionality in the switches.
 */
@Component(immediate = true)
public class TableMissAction {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;



    private static final int DROP_RULE_PRIORITY = 0;

    // decides if a rule to drop any traffic not fitting the
    // 'send to controller rule' is installed or not
    private static final boolean ADD_DROP_RULE = false;

    // mapping the installed flow rules to the device ID for removal at deactivation
    // no multimap needed as only one rule per device installed
    Map<DeviceId, ForwardingObjective> installedFlowRules;

    @Activate
    protected void activate(){
        // create new map
        installedFlowRules = Maps.newHashMap();
        // install the table miss-action drop all rules
        installRules();
    }

    @Deactivate
    protected void deactivate(){
        // remove all installed rules at termination
        for(DeviceId deviceId : installedFlowRules.keySet()){
            flowObjectiveService.forward(deviceId, installedFlowRules.get(deviceId));
        }
        // reset map
        installedFlowRules = null;
    }

    /**
     * Install rules on every network device:
     * First rule:  table miss drop all rule
     */
    private void installRules() {

        Iterable<Device> devices = deviceService.getDevices();

        for(Device device : devices){

            // add default drop rule
            if(ADD_DROP_RULE) {
                DefaultForwardingObjective.Builder forwardingObjective = getDropRuleObjective();
                log.debug("TableMissAction: Adding flow objective \n{} \n" +
                        "for device {} in method installRules()", forwardingObjective, device.id());
                flowObjectiveService.forward(device.id(), forwardingObjective.add());
                // store installed rule
                installedFlowRules.put(device.id(), forwardingObjective.remove());
            }
        }
    }

    /**
     * Generate a permanent objective to drop every packet not matching any other rule
     *
     * @return Forwarding objective for the dropping rule
     */
    private DefaultForwardingObjective.Builder getDropRuleObjective(){

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.ALL);

        TrafficTreatment.Builder trafficTreatmentBuilder = DefaultTrafficTreatment.builder().drop();

        return DefaultForwardingObjective.builder()
                .withSelector(trafficSelectorBuilder.build())
                .withTreatment(trafficTreatmentBuilder.build())
                .withPriority(DROP_RULE_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(applicationIdStore.getAppId(APPLICATION_ID))
                .makePermanent();
    }

}
