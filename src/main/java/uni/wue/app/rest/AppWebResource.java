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
import org.onosproject.rest.AbstractWebResource;

import org.slf4j.Logger;
import uni.wue.app.connection.Connection;
import uni.wue.app.connection.DefaultConnection;
import uni.wue.app.connection.ConnectionStoreService;

import static org.slf4j.LoggerFactory.getLogger;

import javax.ws.rs.PUT;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

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


    /**
     * Allow a host with srcIP_ and srcMac_ to send packets to dstPort_ on dstIp_
     *
     * @param srcIp_ source IP address
     * @param srcMac_ source Mac address
     * @param dstIp_ destination IP address
     * @param dstTpPort_ destination transport protocol port
     * @return Intent state "INSTALLED" if successful,
     *         server error or "FAILED" if failed to add traffic rule
     */
    @PUT
    @Path("/add/{ip}/{mac}/{destIp}/{destTpPort}")
    public Response allowHostTraffic(@PathParam("ip") String srcIp_,
                                    @PathParam("mac") String srcMac_,
                                    @PathParam("destIp") String dstIp_,
                                    @PathParam("destTpPort") String dstTpPort_){
        log.info("adding flow: src ip = {}, src mac = {} -> dst ip = {}, dst TpPort = {}",
                new String[]{srcIp_, srcMac_, dstIp_, dstTpPort_});

        if(srcIp_ == null || srcMac_ == null || dstIp_ == null || dstTpPort_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address srcIp, dstIp;
        MacAddress srcMac;
        TpPort dstTpPort;

        try{
            srcIp = Ip4Address.valueOf(srcIp_);
            srcMac = MacAddress.valueOf(srcMac_);
            dstIp = Ip4Address.valueOf(dstIp_);
            dstTpPort = TpPort.tpPort(Integer.valueOf(dstTpPort_));
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        get(ConnectionStoreService.class).addConnection(new DefaultConnection(srcIp, srcMac, dstIp, dstTpPort));

        return Response.ok(OPERATION_INSTALLED).build();
    }

    @GET
    @Path("/get")
    public Response showRules(){

        Iterable<Connection> connections = get(ConnectionStoreService.class).getConnections();
        return Response.ok(encodeArray(Connection.class, "connections", connections)).build();
    }

    @GET
    @Path("/getUser/{srcIp}/{srcMac}")
    public Response getUserRules(@PathParam("srcIp") String srcIp_,
                             @PathParam("srcMac") String srcMac_){
        log.debug("Getting rules for srcIp = {} and srcMac = {}", srcIp_, srcMac_);
        if(srcIp_ == null || srcMac_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address srcIp;
        MacAddress srcMac;
        try{
            srcIp = Ip4Address.valueOf(srcIp_);
            srcMac = MacAddress.valueOf(srcMac_);
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Iterable<Connection> connections = get(ConnectionStoreService.class).getConnections(srcIp, srcMac);
        return Response.ok(encodeArray(Connection.class, "connections", connections)).build();
    }

    @GET
    @Path("/getService/{dstIp}/{dstTpPort}")
    public Response getServiceRules(@PathParam("dstIp") String dstIp_,
                                    @PathParam("dstTpPort") String dstTpPort_){
        log.debug("Getting rules for dstIp = {} and dstTpPort = {}", dstIp_, dstTpPort_);
        if(dstIp_ == null || dstTpPort_ == null)
            return Response.ok(INVALID_PARAMETER).build();

        Ip4Address dstIp;
        TpPort dstTpPort;
        try{
            dstIp = Ip4Address.valueOf(dstIp_);
            dstTpPort = TpPort.tpPort(Integer.valueOf(dstTpPort_));
        } catch (Exception e){
            return Response.ok(INVALID_PARAMETER).build();
        }

        Iterable<Connection> connections = get(ConnectionStoreService.class).getConnections(dstIp, dstTpPort);
        return Response.ok(encodeArray(Connection.class, "connections", connections)).build();
    }
}
