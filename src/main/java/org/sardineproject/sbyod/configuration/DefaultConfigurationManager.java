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

package org.sardineproject.sbyod.configuration;

import com.google.common.collect.ImmutableSet;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Deactivate;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.ApplicationIdStore;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.sardineproject.sbyod.portal.PortalManager;
import org.sardineproject.sbyod.portal.PortalService;
import org.sardineproject.sbyod.connection.*;
import org.sardineproject.sbyod.consul.ConsulService;
import org.sardineproject.sbyod.dns.DnsService;
import org.sardineproject.sbyod.internet.InternetService;
import org.sardineproject.sbyod.service.Service;
import org.slf4j.Logger;

import java.util.Set;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 17.06.16.
 */
@Component(immediate = true)
public class DefaultConfigurationManager{


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationIdStore applicationIdStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PortalService portalService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DnsService dnsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConsulService consulService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InternetService internetService;



    private static final String APPLICATION_ID = PortalService.APP_ID;
    private static final Logger log = getLogger(PortalManager.class);

    private final InternalConfigListener cfgListener = new InternalConfigListener();

    private final Set<ConfigFactory> factories = ImmutableSet.of(
            new ConfigFactory<ApplicationId, ByodConfig>(APP_SUBJECT_FACTORY,
                    ByodConfig.class,
                    "sbyod") {
                @Override
                public ByodConfig createConfig() {
                    return new ByodConfig();
                }
            }
    );



    @Activate
    protected void activate(){
        // configuration listener
        factories.forEach(cfgService::registerConfigFactory);
        ByodConfig byodConfig = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
        cfgListener.reconfigureNetwork(byodConfig);
        cfgService.addListener(cfgListener);
    }

    @Deactivate
    protected void deactivate(){
        cfgService.removeListener(cfgListener);
        factories.forEach(cfgService::unregisterConfigFactory);
    }



    private class InternalConfigListener implements NetworkConfigListener {

        private void reconfigureNetwork(ByodConfig cfg){
            if(cfg == null){
                return;
            }

            // check if portal config is set and try to connect to new portal location
            // assume that both fields are defined
            if(cfg.portalIp() != null && cfg.portalPort() != -1){

                // get the portal service
                Service portal = portalService.getPortalService();

                // only change and update if portal config has changed
                if(portal == null ||
                        !portal.ipAddressSet().contains(cfg.portalIp()) ||
                        !portal.tpPort().equals(TpPort.tpPort(cfg.portalPort()))) {

                    portalService.setPortal(cfg.portalIp(), TpPort.tpPort(cfg.portalPort()));

                    log.info("DefaultConfigurationManager: Set portal ip to {} and port to {} and updated portal",
                            cfg.portalIp(), cfg.portalPort());
                }
            }

            // todo: update connections routing to default gateway!
            // activate the dns service if a default gateway is defined
            if(cfg.defaultGateway() != null){
                dnsService.deactivateDns();
                dnsService.activateDns();
                log.info("DefaultConfigurationManager: Configured dns service for default gateway={}",
                        cfg.defaultGateway());
                internetService.start();
                log.info("DefaultConfigurationManager: Enabled internet service for default gateway={}",
                        cfg.defaultGateway());
            } else{
                dnsService.deactivateDns();
                internetService.stop();
                log.info("DefaultConfigurationManager: Disabled Internet and DNS service for default gateway={}",
                        cfg.defaultGateway());
            }


            // check if consul config is set and try to set up consul connection
            if(cfg.consulIp() != null && cfg.consulPort() != -1){
                // only change and update if consul config has changed
                if(!(cfg.consulIp().equals(consulService.getConsulIp())) ||
                        !(TpPort.tpPort(cfg.consulPort())).equals(consulService.getConsulTpPort())){
                    // connect to new consul client
                    consulService.connectConsul(cfg.consulIp(), TpPort.tpPort(cfg.consulPort()));
                    log.info("DefaultConfigurationManager: Configured consul ip={} and tpPort={}",
                            cfg.consulIp(), cfg.consulPort());
                }
            } else if(cfg.consulIp() != null){
                // only change and update if consul config has changed
                if(!consulService.getConsulIp().equals(cfg.consulIp())){
                    // connect to new consul client
                    consulService.connectConsul(cfg.consulIp());
                    log.info("DefaultConfigurationManager: Configured consul ip={}", cfg.consulIp());
                }
            }

            // if rule match eth dst has changed
            if(cfg.matchEthDst() != DefaultConnectionRuleInstaller.MATCH_ETH_DST){
                DefaultConnectionRuleInstaller.MATCH_ETH_DST = cfg.matchEthDst();
                // update all installed connections
                Set<Connection> connections = connectionStore.getConnections();
                connections.forEach(c -> connectionStore.removeConnection(c));
                connections.forEach(c -> {Connection newConnection = new DefaultConnection(c.getUser(), c.getService());
                                            connectionStore.addConnection(newConnection);});
                log.info("DefaultConfigurationManager: Updated connections to matchEthDst = {}", cfg.matchEthDst());
            }
        }

        /**
         * Reacts to the specified event.
         *
         * @param event event to be processed
         */
        @Override
        public void event(NetworkConfigEvent event) {

            if(event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
                    event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED &&
                            event.configClass().equals(ByodConfig.class)){

                ByodConfig cfg = cfgService.getConfig(applicationIdStore.getAppId(APPLICATION_ID), ByodConfig.class);
                reconfigureNetwork(cfg);
                log.info("DefaultConfigurationManager: Reconfigured!");
            }
        }
    }
}
