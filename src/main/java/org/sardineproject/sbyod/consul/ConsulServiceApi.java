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
package org.sardineproject.sbyod.consul;

import com.ecwid.consul.transport.TransportException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import com.ecwid.consul.v1.health.model.HealthService;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.sardineproject.sbyod.portal.PortalManager;
import org.sardineproject.sbyod.connection.DefaultConnection;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.ServiceId;
import org.slf4j.Logger;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.service.Service;
import org.sardineproject.sbyod.service.ServiceStore;

import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 19.04.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class ConsulServiceApi implements ConsulService {

    private static final long WAIT_TIME = 50; // seconds - 5*60 is default consul wait time (max wait time = 60*10 s)
    // consul api throws read time out at 60s in com.ecwid.consul.transport.AbstractHttpTransport class

    private static final Logger log = getLogger(PortalManager.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ServiceStore serviceStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ConnectionStore connectionStore;


    protected ConsulClient consulClient;
    private IpAddress consulIp;
    private TpPort consulTpPort;

    // thread checking the consul services every WAIT_TIME seconds
    protected Thread checkServices;


    @Activate
    protected void activate() {
        checkServices = new CheckConsulCatalogServiceUpdates();
        checkServices.setDaemon(true);
    }

    @Deactivate
    protected void deactivate() {
        try {
            if(checkServices.isAlive()) {
                checkServices.interrupt();
            }
        } catch(Exception e){
            log.warn(e.toString());
        }
    }


    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @param tpPort    transport protocol port
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress, TpPort tpPort) {

        if(checkConnection(ipAddress, tpPort)) {
            // remove old services
            disconnectConsul();

            consulClient = new ConsulClient(ipAddress.toString(), tpPort.toInt());
            consulIp = ipAddress;
            consulTpPort = tpPort;

            // add all services from consul to the service store
            Set<Service> consulServices = getServices();
            consulServices.forEach(s -> serviceStore.addService(s));

            // start the thread checking for consul service updates
            checkServices.start();

            return true;
        } else {
            return false;
        }

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
     * Disconnect the running consul agent
     */
    @Override
    public void disconnectConsul() {

        // interrupt the service update thread if it is running
        if(checkServices.isAlive()) {
            checkServices.interrupt();
        }

        // get all services registered by consul from the service store
        Set<Service> storeServices = getConsulServicesFromStore();
        // remove consul services in the service store
        storeServices.forEach(s -> serviceStore.removeService(s));

        consulClient = null;
        consulIp = null;
        consulTpPort = null;

        // restore service update thread
        checkServices = new CheckConsulCatalogServiceUpdates();
        checkServices.setDaemon(true);

    }

    /**
     * Get the ip address the consul client is running on
     *
     * @return Ip address
     */
    @Override
    public IpAddress getConsulIp() {
        if(consulClient != null){
            return consulIp;
        }
        return null;
    }

    /**
     * Get the transport protocol port the consul client is running on
     *
     * @return transport protocol port
     */
    @Override
    public TpPort getConsulTpPort() {
        if(consulClient != null){
            return consulTpPort;
        }
        return null;
    }

    /**
     * Checks the connection to the consul client
     *
     * @param ipAddress IP address of the consul client
     * @param tpPort Transport protocol port of the consul client
     * @return true if connection is available, false otherwise
     */
    private boolean checkConnection(IpAddress ipAddress, TpPort tpPort){
        try{
            // creating consul client to test connection for
            ConsulClient testConsulClient = new ConsulClient(ipAddress.toString(), tpPort.toInt());
            // throws transport exception if no connection to client is available
            testConsulClient.getCatalogServices(new QueryParams(""));
        } catch (TransportException transportException){
            log.warn("ConsulServiceApi: No connection to consul client possible!");
            return false;
        } catch (Exception e){
            log.warn("ConsulServiceApi: Exception while connecting to Consul. Exception: {}", e);
            return false;
        }
        return true;
    }


    /**
     * Get all services marked as Consul discovery from the service store
     * @return Set of services
     */
    private Set<Service> getConsulServicesFromStore(){
        return serviceStore.getServices().stream()
                .filter(s -> s.serviceDiscovery().equals(Service.Discovery.CONSUL))
                .collect(Collectors.toSet());
    }

    /**
     * Gather all services from the consul application agent
     * @return Set of services
     */
    private Set<Service> getServices(){

        Set<Service> consulServices = new HashSet<>();

        QueryParams queryParams = new QueryParams("");

        if(consulClient != null) {
            // get the registered service names
            Map<String, List<String>> mapOfServices = consulClient.getCatalogServices(queryParams).getValue();

            // show the services in log
            mapOfServices.forEach((s, t) -> log.debug("ConsulServiceApi: Found consul service [" + s + " : " + t + "]."));

            // list of services registered in consul
            List<List<CatalogService>> serviceDescription = new LinkedList<>();
            // get the information of the services stored in consul
            for(Map.Entry<String, List<String>> entry : mapOfServices.entrySet()){
                // the name to search for
                String serviceName = entry.getKey();
                // do not announce the consul service
                if(!serviceName.equals("consul")) {

                    try {
                        // encode name to url - replacing spaces with '%20' for example
                        URL url = new URI("http", "example.com", "/" + serviceName + "/", "").toURL();
                        // get only the path part of the url
                        String serviceNameEncoded = url.getPath();
                        // remove the backspaces
                        serviceNameEncoded = serviceNameEncoded.substring(1, serviceNameEncoded.length() - 1);

                        // query consul for the service description and bundle combined services together
                        serviceDescription.add(Lists.newLinkedList(consulClient.getCatalogService(serviceNameEncoded, queryParams).getValue()));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }

                }
            }

            // add the catalog services to the collection
            for (List<CatalogService> catalogServiceList : serviceDescription) {
                    // create services and add them to the service store
                    addServiceToCollection(catalogServiceList, consulServices);
            }
        }

        return consulServices;
    }

    private void addServiceToCollection(List<CatalogService> catalogServiceList, Collection<Service> consulServices) {

        // get the set of service IP addresses
        Set<Ip4Address> ip4AddressSet = Sets.newHashSet();
        for(CatalogService catalogService : catalogServiceList) {
            if (checkService(catalogService)) {
                try {
                    if (catalogService.getServiceAddress().isEmpty()) {
                        // use the default ip address of the consul cluster
                        ip4AddressSet.add(Ip4Address.valueOf(catalogService.getAddress()));
                    } else {
                        // get the ip address of the service
                        ip4AddressSet.add(Ip4Address.valueOf(catalogService.getServiceAddress()));
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("ConsulServiceApi: No correct ip address format = {}, Error: {}",
                            catalogService.getServiceAddress(), e);
                    return;
                }
            }
        }

        // create a new byod service corresponding to the CatalogService
        DefaultService.Builder service = DefaultService.builder()
                .withIp(ip4AddressSet)
                .withPort(TpPort.tpPort(catalogServiceList.iterator().next().getServicePort()))
                .withName(catalogServiceList.iterator().next().getServiceName())
                .withElementId(ServiceId.serviceId(URI.create(catalogServiceList.iterator().next().getServiceId())))
                .withDiscovery(Service.Discovery.CONSUL);

        // add an icon to the service, defined as the first tag in description
        if (!catalogServiceList.iterator().next().getServiceTags().isEmpty()) {
            service.withIcon(catalogServiceList.iterator().next().getServiceTags().iterator().next());
        }

        consulServices.add(service.build());
        log.debug("ConsulServiceApi: Added service {} to collection.", service.build());

    }

    private boolean checkService(CatalogService catalogService){
        boolean isPassing = false;

        QueryParams queryParams = new QueryParams("");
        if(consulClient != null){

            try {
                // encode name to url - replacing spaces with '%20' for example
                URL url = new URI("http", "example.com", "/" + catalogService.getServiceName() + "/", "").toURL();
                // get only the path part of the url
                String serviceNameEncoded = url.getPath();
                // remove the backspaces
                serviceNameEncoded = serviceNameEncoded.substring(1, serviceNameEncoded.length() - 1);

                // query consul for the service description and bundle combined services together
                List<HealthService> healthServices = consulClient.getHealthServices(serviceNameEncoded, true, queryParams).getValue();
                for(HealthService hs : healthServices){
                    log.debug("ConsulService Checks: HealthService:\n{}", hs);
                    log.debug("ConsulService Checks:\nhealth Service Address = {}\nCatalog Service Address = {}", hs.getService().getAddress(), catalogService.getServiceAddress());
                    if(hs.getService().getAddress().equals(catalogService.getServiceAddress())){
                        isPassing = true;
                    }
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        log.debug("ConsulServiceChecks: Is Passing: {}", isPassing);
        return isPassing;
    }

    private class CheckConsulCatalogServiceUpdates extends Thread{

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

            while(consulClient != null && !isInterrupted()) {
                try {
                    // get the consul index to wait for
                    QueryParams queryParams = new QueryParams("");
                    Response<Map<String, List<String>>> services = consulClient.getCatalogServices(queryParams);

                    // TODO: check what happens if connection to consul is lost while doing blocking query
                    // start blocking query for index
                    queryParams = new QueryParams(WAIT_TIME, services.getConsulIndex());
                    if (!isInterrupted())
                        services = consulClient.getCatalogServices(queryParams);

                    if (!isInterrupted()) {
                        log.info("ConsulServiceApi: Updating consul services - {}", services.toString());
                        updateConsulServices();
                    }
                } catch(TransportException te){
                    log.warn("ConsulServiceApi: Transport exception - check if the consul service is running " +
                            "and restart the consul Sardine-BYOD extension.");
                    disconnectConsul();
                }
            }
        }
    }

    private void updateConsulServices(){

        // get the services registered in consul
        Set<Service> consulServices = getServices();
        // get the consul services from the byod service store
        Set<Service> storeServices = getConsulServicesFromStore();

        for(Service oldService : storeServices){
            // services in consul with same id as old service
            Set<Service> equalConsulServices = consulServices.stream()
                    .filter(cs -> cs.id().equals(oldService.id()))
                    .collect(Collectors.toSet());

            if(equalConsulServices.isEmpty()){
                // service has been deleted
                log.debug("ConsulServiceApi: Service with ServiceId = {} has been deleted.", oldService.id());
                serviceStore.removeService(oldService);

            } else {
                // service is active, but could be changed
                if(equalConsulServices.size() > 1) {
                    // take the first service
                    log.info("ConsulServiceApi: More than one service with same ServiceId = {}. " +
                            "Taking the one with lowest IP address.", oldService.id());
                }
                // take first service in set with lowest ip address
                Service newService = Collections.min(equalConsulServices,
                        (o1, o2) -> o1.ipAddressSet().iterator().next()
                                .compareTo(o2.ipAddressSet().iterator().next()));
                // check for updates
                if (oldService.equals(newService)) {
                    // nothing changed, no update needed
                } else {
                    // service updated, update all connections
                    Set<Connection> connections = connectionStore.getConnections(oldService);
                    // get the connected hosts of the service
                    Set<Host> hosts = connections.stream()
                            .map(Connection::getUser)
                            .collect(Collectors.toSet());

                    // remove old service from store, connections are removed automatically
                    serviceStore.removeService(oldService);
                    // add new service to store
                    serviceStore.addService(newService);

                    // add connection for each host
                    for (Host host : hosts) {
                        connectionStore.addConnection(new DefaultConnection(host, newService));
                    }

                    log.info("ConsulServiceApi: Updated old service = {} to new service = {} and connected hosts = {}.",
                            oldService, newService, hosts);
                }
            }

            // remove the processed services
            consulServices.removeAll(equalConsulServices);
        }

        // add all remaining services as new service to store
        for(Service consulService : consulServices){
            // only add service if no other service with the same name exists
            if(serviceStore.getService(consulService.name()).isEmpty()) {
                serviceStore.addService(consulService);
                log.info("ConsulServiceApi: Added new service = {}", consulService);
            }
        }

    }

}
