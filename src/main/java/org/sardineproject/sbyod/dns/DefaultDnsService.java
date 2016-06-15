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

package org.sardineproject.sbyod.dns;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.Host;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.ProviderId;
import org.sardineproject.sbyod.ByodConfig;
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.connection.DefaultConnection;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.Service;
import org.sardineproject.sbyod.service.ServiceStore;
import org.slf4j.Logger;

import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 08.06.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class DefaultDnsService implements DnsService {

    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;

    // set of dns connections for every host
    private Service dnsService;

    // TODO: add listener for new host and add dns connection

    public void activateDns(){

        // get the ip address of the default gateway
        ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        // get the host with ip of the default gateway
        Set<Host> routers = hostService.getHostsByIp(cfg.defaultGateway());

        // if exactly one host was found
        if(routers.size() == 1){
            // get the only one router host
            Host router = routers.iterator().next();

            dnsService = new DefaultService(router, TpPort.tpPort(53), "DnsService", ProviderId.NONE);
            serviceStore.addService(dnsService);

            // connect all valid hosts to the dns service
            for(Host host : hostService.getHosts()){
                // do not install the service for the router itself
                if(!host.equals(router)){
                    Connection connection = new DefaultConnection(host, dnsService);
                    connectionStore.addConnection(connection);
                }
            }
            // TODO: remove dns in "DefaultBasicRuleInstaller"
        } else if(routers.isEmpty()){
            log.warn("BasicRuleInstaller: No host found with IP={} to use as DNS service", cfg.defaultGateway());
        } else{
            log.warn("BasicRuleInstaller: More than one host found with IP={} to use as DNS service", cfg.defaultGateway());
        }

    }

    public void deactivateDns(){
        // get all connections to the dns router
        Set<Connection> dnsConnections = connectionStore.getConnections(dnsService);
        // remove all connections of the dns router
        dnsConnections.forEach(dnsC -> connectionStore.removeConnection(dnsC));
        // remove the dns service from the service store
        serviceStore.removeService(dnsService);
        dnsService = null;
    }
}
