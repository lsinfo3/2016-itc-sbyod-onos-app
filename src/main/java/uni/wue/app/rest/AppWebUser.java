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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.rest.AbstractWebResource;

import org.slf4j.Logger;
import uni.wue.app.connection.Connection;
import uni.wue.app.connection.ConnectionStore;
import uni.wue.app.connection.DefaultConnection;
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceId;
import uni.wue.app.service.ServiceStore;

import static org.slf4j.LoggerFactory.getLogger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Web resource.
 */
@Path("/user")
public class AppWebUser extends AbstractWebResource {

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private static final String INVALID_PARAMETER = "INVALID_PARAMETER\n";
    private final ObjectNode ENABLED_TRUE = mapper().createObjectNode().put("enabled", true);
    private final ObjectNode ENABLED_FALSE = mapper().createObjectNode().put("enabled", false);


    /**
     * Get the services the user with userIp is connected to.
     *
     * @param userIp_ the IP address of the user
     * @return INVALID_PARAMETER if some parameter was wrong
     *          array of services
     */
    @GET
    @Path("/{userIp}")
    public Response getUserRules(@PathParam("userIp") String userIp_){
        log.debug("AppWebUser: Getting services for userIp = {}", userIp_);

        if(userIp_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address userIp;
        try{
            userIp = Ip4Address.valueOf(userIp_);
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        // return invalid, if no user with this ip address is connected
        if(get(HostService.class).getHostsByIp(userIp).isEmpty())
            return Response.ok(INVALID_PARAMETER).build();

        // get the services the user is allowed to use
        Iterable<Service> services = get(ServiceStore.class).getServices();
        Set<Service> userServices = get(ConnectionStore.class).getUserConnections(userIp).stream()
                .map(c -> c.getService())
                .collect(Collectors.toSet());

        ArrayNode arrayNode = mapper().createArrayNode();

        for(Service service : services){
            ObjectNode serviceNode = mapper().createObjectNode()
                    .put("serviceName", service.getName())
                    .put("serviceId", service.id().toString())
                    .put("serviceTpPort", service.getTpPort().toString())
                    .put("icon", service.getIcon());
            if(userServices.contains(service))
                serviceNode.put("serviceEnabled", true);
            else
                serviceNode.put("serviceEnabled", false);

            arrayNode.add(serviceNode);
        }

        JsonNode result = mapper().createObjectNode().set("services", arrayNode);
        return Response.ok(result).build();

    }

    /**
     * Ask if a user with userIp has a access to the service with serviceId.
     *
     * @param userIp_ the IP address of the user
     * @param serviceId_ the ID of the service
     * @return INVALID_PARAMETER if some parameter was wrong
     *          "enabled : false" if service is disabled
     *          "enabled : true" if service is enabled
     */
    @GET
    @Path("/{userIp}/service/{serviceId}")
    public Response getUserServices(@PathParam("userIp") String userIp_,
                                    @PathParam("serviceId") String serviceId_){
        log.debug("AppWebUser: Getting rules for userIp = {} and serviceId = {}", userIp_, serviceId_);

        if(userIp_ == null || serviceId_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address userIp;
        ServiceId serviceId;
        try{
            userIp = Ip4Address.valueOf(userIp_);
            serviceId = ServiceId.serviceId(serviceId_);
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Set<Connection> result = get(ConnectionStore.class).getUserConnections(userIp);
        if(result.stream()
                .filter(c -> c.getService().id().equals(serviceId))
                .count() != 0){
            return Response.ok(ENABLED_TRUE).build();
        }
        return Response.ok(ENABLED_FALSE).build();
    }

    /**
     * Allow a host with userIP to send packets to service with serviceId.
     *
     * @param userIp_ the IP address of the user
     * @param serviceId_ the ID of the service
     * @return INVALID_PARAMETER if some parameter was wrong
     *          "enabled : false" if service connection went wrong
     *          "enabled : true" if service is enabled
     */
    @POST
    @Path("/{userIp}/service/{serviceId}")
    public Response allowHostTraffic(@PathParam("userIp") String userIp_,
                                    @PathParam("serviceId") String serviceId_){
        log.debug("AppWebUser: Adding connection between user ip = {} and serviceId = {}",
                new String[]{userIp_, serviceId_});

        if(userIp_ == null || serviceId_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Service service;
        Set<Host> srcHosts;
        try{
            Ip4Address userIp = Ip4Address.valueOf(userIp_);
            srcHosts = get(HostService.class).getHostsByIp(userIp);
            service = get(ServiceStore.class).getService(ServiceId.serviceId(serviceId_));
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        if(srcHosts.isEmpty()) {
            log.debug("AppWebUser: No host found with IP = {}", userIp_);
            return Response.ok(ENABLED_FALSE).build();
        } else if(service == null) {
            log.debug("AppWebUser: No service found with id = {}", serviceId_);
            return Response.ok(ENABLED_FALSE).build();
        }

        // install connection for every host and service
        for(Host srcHost : srcHosts) {
                Connection connection = new DefaultConnection(srcHost, service);
                // if the connection does not already exist
                if (!get(ConnectionStore.class).contains(connection)) {
                    log.debug("AppWebUser: Installing connection {}", connection.toString());
                    get(ConnectionStore.class).addConnection(connection);
                } else{
                    log.debug("AppWebUser: Connection {} already exists", connection.toString());
                }
        }

        return Response.ok(ENABLED_TRUE).build();
    }

    @DELETE
    @Path("/{userIp}/service/{serviceId}")
    public Response deleteHostTraffic(@PathParam("userIp") String userIp_,
                                      @PathParam("serviceId") String serviceId_){
        log.debug("AppWebUser: Removing connection between user ip = {} and serviceId = {}",
                new String[]{userIp_, serviceId_});

        if(userIp_ == null || serviceId_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Service service;
        Set<Host> srcHosts;
        try{
            Ip4Address userIp = Ip4Address.valueOf(userIp_);
            srcHosts = get(HostService.class).getHostsByIp(userIp);
            service = get(ServiceStore.class).getService(ServiceId.serviceId(serviceId_));
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }


        if(srcHosts.isEmpty()) {
            log.debug("AppWebUser: No host found with IP = {}", userIp_);
            return Response.ok(ENABLED_FALSE).build();
        } else if(service == null) {
            log.debug("AppWebUser: No service found with id = {}", serviceId_);
            return Response.ok(ENABLED_FALSE).build();
        }

        // install connection for every host and service
        for(Host user : srcHosts) {
            Connection connection = get(ConnectionStore.class).getConnection(user, service);
            if(connection != null) {
                log.debug("AppWebUser: Removing connection {}", connection.toString());
                get(ConnectionStore.class).removeConnection(connection);
            }
        }

        return Response.ok(ENABLED_FALSE).build();
    }
}
