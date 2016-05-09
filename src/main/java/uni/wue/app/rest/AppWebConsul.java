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
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import uni.wue.app.consul.ConsulService;
import uni.wue.app.consul.ConsulServiceApi;
import uni.wue.app.consul.ConsulServiceClient;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * REST consul interface.
 */
@Path("/consul")
public class AppWebConsul extends AbstractWebResource{

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private static final String INVALID_PARAMETER = "INVALID_PARAMETER\n";
    private final ObjectNode ENABLED_TRUE = mapper().createObjectNode().put("enabled", true);
    private final ObjectNode ENABLED_FALSE = mapper().createObjectNode().put("enabled", false);

    /**
     * Connecting to consul running on server with IP and transport protocol port.
     * @param ip_ Consul server IP address
     * @param tpPort_ Consul transport protocol port
     * @return "enabled: true" if connection to server is active
     */
    @POST
    @Path("/ip/{ip}/tpPort/{tpPort}")
    public Response postConsul(@PathParam("ip") String ip_,
                               @PathParam("tpPort") String tpPort_){
        log.debug("AppWebConsul: Connecting to consul on {}", ""+ip_+":"+tpPort_);

        if(ip_ == null || tpPort_ == null){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Ip4Address ip4Address;
        TpPort tpPort;
        try{
            ip4Address = Ip4Address.valueOf(ip_);
            tpPort = TpPort.tpPort(Integer.valueOf(tpPort_));
        } catch(Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        try {
            ConsulService consulService = get(ConsulService.class);
            consulService.connectConsul(ip4Address, tpPort);
            return Response.ok(ENABLED_TRUE).build();
        } catch(Exception e){
            return Response.ok(ENABLED_FALSE).build();
        }
    }

    /**
     * Connecting to consul running on server with IP
     * @return "enabled: true" if connection to server is active
     */
    @POST
    @Path("/ip/{ip}")
    public Response postConsul(@PathParam("ip") String ip_){
        log.debug("AppWebConsul: Connecting to consul on {}, port 8500", ip_);

        if(ip_ == null){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Ip4Address ip4Address;
        try{
            ip4Address = Ip4Address.valueOf(ip_);
        } catch(Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        try {
            ConsulService consulService = get(ConsulService.class);
            if(consulService.connectConsul(ip4Address)){
                return Response.ok(ENABLED_TRUE).build();
            } else{
                return Response.ok(ENABLED_FALSE).build();
            }
        } catch(Exception e){
            return Response.ok(ENABLED_FALSE).build();
        }
    }
}
