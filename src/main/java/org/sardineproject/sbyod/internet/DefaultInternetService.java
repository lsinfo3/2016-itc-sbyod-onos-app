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
 */

package org.sardineproject.sbyod.internet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ip4Address;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.Service;
import org.sardineproject.sbyod.service.ServiceStore;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 18.07.16.
 */
@Component
@org.apache.felix.scr.annotations.Service
public class DefaultInternetService implements InternetService {

    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;


    private Service internetService;


    /**
     * Activate the service to install internet rules for every device in the network
     */
    @Override
    public void start() {
        // create internet service
        this.internetService = DefaultService.builder().withName("Internet").withIp(Ip4Address.valueOf("0.0.0.0")).build();
        if(serviceStore != null) {
            // adding service to store
            serviceStore.addService(internetService);
        } else {
            log.warn("InternetService: No ServiceStore defined!");
        }
    }

    /**
     * Remove all flow rules allowing internet access
     */
    @Override
    public void stop() {
        if(serviceStore != null) {
            // removing service from store
            serviceStore.removeService(internetService);
            this.internetService = null;
        } else {
            log.warn("InternetService: No ServiceStore defined!");
        }
    }


}
