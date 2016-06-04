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

import com.google.common.collect.Sets;
import org.onosproject.net.Host;
import org.onosproject.net.flow.FlowRule;
import org.sardineproject.sbyod.PortalManager;
import org.slf4j.Logger;
import org.sardineproject.sbyod.service.Service;

import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
public class DefaultConnection implements Connection{

    private static final Logger log = getLogger(PortalManager.class);

    private final Host user;
    private final Service service;
    private Set<FlowRule> flowRules;


    public DefaultConnection(Host user, Service service){

        if(user == null || service == null){
            log.warn("DefaultConnection: Invalid parameter");
            throw new InvalidParameterException(String.format("Invalid parameter in class : %s",
                    this.getClass().toString()));
        }

        this.user = user;
        this.service = service;
        flowRules = new HashSet<>();
    }

    public Service getService() { return service; }

    public Host getUser(){ return user;}

    /**
     * Add a flow rule establishing the connection
     *
     * @param flowRule flow rule utilized in connection
     */
    @Override
    public void addFlowRule(FlowRule flowRule) {
        if(flowRule == null)
            return;

        flowRules.add(flowRule);
    }

    /**
     * Get the flow rules establishing the connection
     *
     * @return set of flow rules
     */
    @Override
    public Set<FlowRule> getFlowRules() {
        return Sets.newHashSet(flowRules);
    }


}

