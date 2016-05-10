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
package uni.wue.app.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import uni.wue.app.service.DefaultService;
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceId;
import uni.wue.app.service.ServiceStore;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Web service interface.
 */
@Path("/service")
public class AppWebService extends AbstractWebResource {

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

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

        Iterable<Service> services = get(ServiceStore.class).getServices();
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

        Set<Host> serviceHosts = get(HostService.class).getHostsByIp(ip);
        if(serviceHosts.size() == 1){
            Service service = new DefaultService(serviceHosts.iterator().next(), tpPort, name, ProviderId.NONE);

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

        } else if(serviceHosts.size() == 0){
            log.warn("AppWebService: No host found with ip={}", ip);
            return Response.ok(ENABLED_FALSE).build();
        } else{
            log.warn("AppWebService: More than one host found with ip={}", ip);
            return Response.ok(ENABLED_FALSE).build();
        }
    }

    @DELETE
    @Path("/serviceID/{id}")
    public Response deleteService(@PathParam("id") String id_){

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
        if(service != null) {
            if(get(ServiceStore.class).removeService(service)){
                return Response.ok(ENABLED_FALSE).build();
            } else{
                return Response.ok(ENABLED_TRUE).build();
            }
        }
        return Response.ok(ENABLED_FALSE).build();

    }

}
