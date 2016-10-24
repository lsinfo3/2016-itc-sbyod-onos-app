package org.sardineproject.sbyod.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.DeviceId;
import org.onosproject.rest.AbstractWebResource;
import org.sardineproject.sbyod.cli.completer.DeviceIdCompleter;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.portal.PortalManager;
import org.slf4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.util.Iterator;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by Bene on 21.10.16.
 * returns connections consiting of (user, active services, flow objective count)
 * works like cli command "list-connections"
 */
@Path("/connections")
public class AppWebConnection extends AbstractWebResource {

    private static final Logger log = getLogger(PortalManager.class);
    private ConnectionStore connectionStore;

    private static final String INVALID_PARAMETER = "INVALID_PARAMETER\n";
    private final ObjectNode ENABLED_TRUE = mapper().createObjectNode().put("enabled", true);
    private final ObjectNode ENABLED_FALSE = mapper().createObjectNode().put("enabled", false);

    /**
     * Get all active connections
     *
     * @return array of connections
     */
    @GET
    @Path("/connections")
    public Response getServices(){
        log.debug("AppWebUser: Getting all active connections");
        connectionStore = get(ConnectionStore.class);
        Iterator<Connection> connectionIterator = connectionStore.getConnections().iterator();


        ArrayNode arrayNode = mapper().createArrayNode();

        while(connectionIterator.hasNext()){
            Connection connection = connectionIterator.next();

            ObjectNode connectionNode = mapper().createObjectNode();

            ObjectNode userNode = mapper().createObjectNode()
                    .put("id", connection.getUser().id().toString())
                    .put("mac", connection.getUser().mac().toString())
                    .put("vlan", connection.getUser().vlan().toString())
                    .put("ipAddress", connection.getUser().ipAddresses().iterator().next().toString());
                    //.put("location", connection.getUser().location());

            ObjectNode serviceNode = mapper().createObjectNode()
                    .put("serviceName", connection.getService().name().toString())
                    .put("serviceId", connection.getService().id().toString())
                    .put("serviceTpPort", connection.getService().tpPort().toString())
                    .put("ip4Address", connection.getService().ipAddressSet().iterator().next().toString());

            //connection.getForwardingObjectives().size();
            ObjectNode forwardingObjectives = mapper().createObjectNode()
                    .put("selector", connection.getForwardingObjectives().keySet().iterator().next().selector().toString())
                    .put("deviceId", connection.getForwardingObjectives().values().toString());

            /*ArrayNode deviceArray = mapper().createArrayNode();
            for(DeviceId deviceId : connection.getForwardingObjectives().values())
                for(Map.Entry entry : connection.getForwardingObjectives().entrySet()){
                    if(entry.getValue().equals(deviceId))

                }*/

            connectionNode.set("user", userNode);
            connectionNode.set("service", serviceNode);
            connectionNode.set("forwardingObjective", forwardingObjectives);

            arrayNode.add(connectionNode);

        }



        JsonNode result = mapper().createObjectNode().set("connections", arrayNode);
        return Response.ok(result).build();
    }

}
