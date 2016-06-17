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
import org.sardineproject.sbyod.PortalManager;
import org.sardineproject.sbyod.PortalService;
import org.sardineproject.sbyod.consul.ConsulService;
import org.sardineproject.sbyod.dns.DnsService;
import org.sardineproject.sbyod.service.Service;
import org.slf4j.Logger;

import java.util.Set;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 17.06.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class DefaultConfigurationManager implements ConfigurationManager{


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

            // get the portal service
            Service portal = portalService.getPortalService();

            // check if portal config is set and try to connect to new portal location
            if(cfg.portalIp() != null && cfg.portalPort() != -1){

                // only change and update if portal config has changed
                if(!portal.getHost().ipAddresses().contains(cfg.portalIp()) ||
                        !portal.getTpPort().equals(TpPort.tpPort(cfg.portalPort()))) {

                    portalService.setPortal(cfg.portalIp(), TpPort.tpPort(cfg.portalPort()));
                    log.info("DefaultConfigurationManager: Set portal ip to {} and port to {} and updated portal", cfg.portalIp(), cfg.portalPort());
                }
            } else if(cfg.portalIp() != null){

                // only change and update if portal config has changed
                if(!portal.getHost().ipAddresses().contains(cfg.portalIp())) {

                    // set the new ip and leave the old tp port
                    portalService.setPortal(cfg.portalIp(), portal.getTpPort());
                    log.info("DefaultConfigurationManager: Set portal ip to {} and updated portal", cfg.portalIp());
                }
            } else{
                log.info("DefaultConfigurationManager: No portal configured");
            }


            // activate the dns service if a default gateway is defined
            if(cfg.defaultGateway() != null){
                dnsService.deactivateDns();
                dnsService.activateDns();
                log.info("DefaultConfigurationManager: Activated dns service for default gateway={}", cfg.defaultGateway());
            }


            // check if consul config is set and try to set up consul connection
            if(cfg.consulIp() != null && cfg.consulPort() != -1){
                // only change and update if consul config has changed
                if(!(cfg.consulIp().equals(consulService.getConsulIp())) ||
                        !(TpPort.tpPort(cfg.consulPort())).equals(consulService.getConsulTpPort())){
                    // connect to new consul client
                    consulService.connectConsul(cfg.consulIp(), TpPort.tpPort(cfg.consulPort()));
                    log.info("PortalManager: Configured consul ip={} and tpPort={}", cfg.consulIp(), cfg.consulPort());
                }
            } else if(cfg.consulIp() != null){
                // only change and update if consul config has changed
                if(!consulService.getConsulIp().equals(cfg.consulIp())){
                    // connect to new consul client
                    consulService.connectConsul(cfg.consulIp());
                    log.info("PortalManager: Configured consul ip={}", cfg.consulIp());
                }
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
                log.info("Reconfigured!");
            }
        }
    }
}
