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
package uni.wue.app.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;
import uni.wue.app.service.DefaultService;
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceStore;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 19.04.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class ConsulServiceApi implements ConsulService {

    private static final long WAIT_TIME = 60*5; // seconds - 5*60 is default consul wait time (max wait time = 60*10 s)

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    protected ConsulClient consulClient;
    protected Thread checkServices;


    @Activate
    protected void activate() {
        checkServices = new Thread(new CheckConsulCatalogServiceUpdates());
    }

    @Deactivate
    protected void deactivate() {
        checkServices.interrupt();
    }


    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @param tpPort    transport protocol port
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress, TpPort tpPort) {

        consulClient = new ConsulClient(ipAddress.toString(), tpPort.toInt());

        // get all services discovered from consul
        Set<Service> storeServices = serviceStore.getServices().stream()
                .filter(s -> s.getServiceDiscovery().equals(Service.Discovery.CONSUL))
                .collect(Collectors.toSet());

        // remove old consul services
        storeServices.forEach(s -> serviceStore.removeService(s));

        // add all services from consul to the service store
        updateServices();

        // start the thread checking for consul service updates
        checkServices.start();

        return true;
    }

    /**
     * Connect to a running consul agent on TpPort 8500.
     *
     * @param ipAddress Ip address to connect to
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress) {
        return connectConsul(ipAddress, TpPort.tpPort(8500));
    }

    /**
     * Adding all services found by consul to the service store
     */
    private void updateServices(){

        QueryParams queryParams = new QueryParams("");

        // get the registered service names
        Map<String, List<String>> services = consulClient.getCatalogServices(queryParams).getValue();

        // show the services in log
        services.forEach((s, t) -> log.info("Found consul service [" + s + " : " + t + "]."));

        // get the information stored about the services
        List<CatalogService> serviceDescription = new LinkedList<>();
        services.forEach((s,t) -> serviceDescription.addAll(consulClient.getCatalogService(s.toString(), queryParams)
                .getValue()));

        // add the catalog services to the ServiceStore
        for(CatalogService c : serviceDescription){

            // get all hosts with corresponding ip address
            Set<Host> hosts;
            if(c.getServiceAddress().isEmpty()){
                // default ip address is the consul ip address
                hosts = hostService.getHostsByIp(IpAddress.valueOf(c.getAddress()));
            } else{
                try {
                    hosts = hostService.getHostsByIp(IpAddress.valueOf(c.getServiceAddress()));
                } catch(IllegalArgumentException e){
                    log.warn(e.toString());
                    hosts = Sets.newHashSet();
                }
            }

            // add all discovered hosts running the service to the ServiceStore
            for(Host h : hosts) {
                log.info("Adding consul service {} running on {} to service store.",
                        c.getServiceName(), h.ipAddresses());

                Service service = new DefaultService(h, TpPort.tpPort(c.getServicePort()), c.getServiceName(),
                        ProviderId.NONE, c.getServiceId(), Service.Discovery.CONSUL);
                serviceStore.addService(service);
            }
        }
    }

    private class CheckConsulCatalogServiceUpdates implements Runnable{

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {

            while(consulClient != null) {
                // get the consul index to wait for
                QueryParams queryParams = new QueryParams("");
                Response<Map<String, List<String>>> services = consulClient.getCatalogServices(queryParams);

                // start blocking query for index
                queryParams = new QueryParams(WAIT_TIME, services.getConsulIndex());
                services = consulClient.getCatalogServices(queryParams);

                log.info("Blocking query result: " + services.toString());

                // TODO: check services in service store for modification
            }
        }
    }

}
