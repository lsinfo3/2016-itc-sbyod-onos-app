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

import org.onosproject.net.Host;
import org.onosproject.net.flow.FlowRule;
import org.sardineproject.sbyod.service.Service;

import java.util.Set;

/**
 * Created by lorry on 06.03.16.
 */
public interface Connection{

    /**
     * Get the service of the connection
     *
     * @return service
     */
    Service getService();

    /**
     * Get the user of the connection
     *
     * @return user host element
     */
    Host getUser();

    /**
     * Add a flow rule establishing the connection
     *
     * @param flowRule flow rule utilized in connection
     */
    void addFlowRule(FlowRule flowRule);

    /**
     * Get the flow rules establishing the connection
     *
     * @return set of flow rules
     */
    Set<FlowRule> getFlowRules();
}
