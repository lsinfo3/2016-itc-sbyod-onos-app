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

import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.rest.AbstractWebResource;

import org.slf4j.Logger;
import uni.wue.app.connection.Connection;
import uni.wue.app.connection.DefaultConnection;
import uni.wue.app.connection.ConnectionStoreService;
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceStore;

import static org.slf4j.LoggerFactory.getLogger;

import javax.ws.rs.PUT;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Set;

/**
 * Sample web resource.
 */
@Path("byod")
public class AppWebResource extends AbstractWebResource {

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private static final String INVALID_PARAMETER = "INVALID_PARAMETER\n";
    private static final String OPERATION_INSTALLED = "INSTALLED\n";
    private static final String OPERATION_FAILED = "FAILED\n";
    private static final String OPERATION_WITHDRAWN = "WITHDRAWN\n";

    @GET
    @Path("/service/{serviceName}")
    public Response getService(@PathParam("serviceName") String serviceName_){
        if(serviceName_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Iterable<Service> services = get(ServiceStore.class).getService(serviceName_);
        return Response.ok(encodeArray(Service.class, "service", services)).build();
    }

    @GET
    @Path("/user/{srcIp}")
    public Response getUserRules(@PathParam("srcIp") String srcIp_){
        log.debug("Getting rules for srcIp = {}", srcIp_);
        if(srcIp_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address srcIp;
        try{
            srcIp = Ip4Address.valueOf(srcIp_);
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Iterable<Connection> connections = get(ConnectionStoreService.class).getConnections(srcIp);
        return Response.ok(encodeArray(Connection.class, "connections", connections)).build();
    }

    @GET
    @Path("/user/{srcIp}/service/{serviceName}")
    public Response getUserServices(@PathParam("srcIp") String srcIp_,
                                    @PathParam("serviceName") String serviceName_){

        if(srcIp_ == null || serviceName_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address srcIp;
        String serviceName;
        try{
            srcIp = Ip4Address.valueOf(srcIp_);
            serviceName = serviceName_;
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Set<Connection> result = get(ConnectionStoreService.class).getConnections(srcIp);
        if(result.stream()
                .filter(c -> c.getService().getName().equals(serviceName))
                .count() != 0){
            return Response.ok(mapper().createObjectNode().put("enabled", true)).build();
        }
        return Response.ok(mapper().createObjectNode().put("enabled", false)).build();
    }

    /**
     * Allow a host with srcIP_ and srcMac_ to send packets to dstPort_ on dstIp_
     *
     * @param srcIp_ source IP address
     * @param serviceName_ the service name
     * @return Intent state "INSTALLED" if successful,
     *         server error or "FAILED" if failed to add traffic rule
     */
    @POST
    @Path("/user/{srcIp}/service/{serviceName}")
    public Response allowHostTraffic(@PathParam("srcIp") String srcIp_,
                                    @PathParam("serviceName") String serviceName_){
        log.info("adding flow: src ip = {} -> service = {}",
                new String[]{srcIp_, serviceName_});

        if(srcIp_ == null || serviceName_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address srcIp;
        Set<Service> services;
        Host srcHost;

        try{
            srcIp = Ip4Address.valueOf(srcIp_);
            srcHost = get(HostService.class).getHostsByIp(srcIp).iterator().next();
            services = get(ServiceStore.class).getService(serviceName_);
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        if(services.isEmpty()) {
            log.warn("AppWebResource: No service found with name = {}", serviceName_);
            return Response.notModified(OPERATION_FAILED).build();
        }

        for(Service service : services) {
            Connection connection = new DefaultConnection(srcIp, srcHost.mac(),service);
            // if the connection does not already exist
            if(!get(ConnectionStoreService.class).contains(connection)){
                log.debug("Installing connection: {}", connection.toString());
                get(ConnectionStoreService.class).addConnection(connection);
            }
        }

        return Response.ok(OPERATION_INSTALLED).build();
    }
}
