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
package org.sardineproject.sbyod.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.rest.AbstractWebResource;
import org.sardineproject.sbyod.portal.PortalManager;
import org.sardineproject.sbyod.portal.PortalService;
import org.sardineproject.sbyod.dns.DnsService;
import org.sardineproject.sbyod.service.DefaultService;
import org.sardineproject.sbyod.service.Service;
import org.sardineproject.sbyod.service.ServiceId;
import org.sardineproject.sbyod.service.ServiceStore;
import org.slf4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manage the available services.
 */
@Path("/service")
public class AppWebService extends AbstractWebResource {

    private static final Logger log = getLogger(PortalManager.class);

    private static final String INVALID_PARAMETER = "INVALID_PARAMETER\n";
    private final ObjectNode ENABLED_TRUE = mapper().createObjectNode().put("enabled", true);
    private final ObjectNode ENABLED_FALSE = mapper().createObjectNode().put("enabled", false);

    /**
     * Get all registered services.
     *
     * @return array of services
     */
    @GET
    @Path("")
    public Response getServices(){
        log.debug("AppWebUser: Getting all services");

        // do not return the portal service
        Iterable<Service> services = removeConfigurationServices(get(ServiceStore.class).getServices());
        return Response.ok(encodeArray(Service.class, "services", services)).build();
    }

    /**
     * Get the service with serviceId.
     *
     * @param serviceId_ the ID of the service
     * @return INVALID_PARAMETER if some parameter was wrong
     *          service
     */
    @GET
    @Path("/{serviceId}")
    public Response getService(@PathParam("serviceId") String serviceId_){
        log.debug("AppWebUser: Getting service with name = {}", serviceId_);

        if(serviceId_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Service service = get(ServiceStore.class).getService(ServiceId.serviceId(serviceId_));
        Set<Service> result = (service == null) ? Sets.newHashSet() : Sets.newHashSet(service);

        return Response.ok(encodeArray(Service.class, "services", (Iterable)result)).build();
    }

    /**
     * Create a new service.
     *
     * @param ip_ Ip address of the host of the service
     * @param tpPort_ The transport protocol port of the service
     * @param name_ The name of the service
     * @return INVALID_PARAMETER if some parameter was wrong
     */
    @POST
    @Path("/ip/{ip}/tpPort/{tpPort}/name/{name}")
    public Response setServices(@PathParam("ip") String ip_,
                                @PathParam("tpPort") String tpPort_,
                                @PathParam("name") String name_){
        log.debug("AppWebService: Adding service with ip={}, tpPort={} and name={}",
                Lists.newArrayList(ip_,tpPort_,name_).toArray());

        if(ip_ == null || tpPort_ == null || name_ == null){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Ip4Address ip;
        TpPort tpPort;
        String name;
        try{
            ip = Ip4Address.valueOf(ip_);
            tpPort = TpPort.tpPort(Integer.valueOf(tpPort_));
            name = name_;
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        // define the service
            Service service = DefaultService.builder()
                    .withPort(tpPort)
                    .withName(name)
                    .withIp(ip)
                    .build();

            // check if service is already active
            if(!get(ServiceStore.class).contains(service)){
                // check if service was realy installed
                if(get(ServiceStore.class).addService(service)){
                    return Response.ok(ENABLED_TRUE).build();
                } else{
                    return Response.ok(ENABLED_FALSE).build();
                }
            } else{
                return Response.ok(ENABLED_TRUE).build();
            }
    }

    /**
     * Delete a service.
     * @param id_ the service ID
     * @return INVALID_PARAMETER if some parameter was wrong
     */
    @DELETE
    @Path("/{serviceId}")
    public Response deleteService(@PathParam("serviceId") String id_){

        log.debug("AppWebService: Deleting service with id={}.", id_);

        if(id_ == null){
            return Response.ok(INVALID_PARAMETER).build();
        }

        ServiceId id;
        try{
            id = ServiceId.serviceId(id_);
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Service service = get(ServiceStore.class).getService(id);
        Service portalService = get(PortalService.class).getPortalService();
        // deleting the portal service is not allowed
        if(service != null && !service.equals(portalService)) {
            if(get(ServiceStore.class).removeService(service)){
                return Response.ok(ENABLED_FALSE).build();
            } else{
                return Response.ok(ENABLED_TRUE).build();
            }
        }
        return Response.ok(ENABLED_FALSE).build();

    }

    /**
     * This method removes all services from a set that are not intended for the user to manipulate,
     * for example the portal service or the dns service
     * @param services a set of services
     * @return an iterable of services without configuration services
     */
    private Iterable<Service> removeConfigurationServices(Set<Service> services){
        // get the portalService
        Service portalService = get(PortalService.class).getPortalService();
        // get the dns services
        Set<Service> dnsServices = get(DnsService.class).getDnsServices();

        // remove the configuration services
        return services.stream()
                .filter(s -> !s.equals(portalService))
                .filter(s -> !dnsServices.contains(s))
                .collect(Collectors.toSet());
    }

}
