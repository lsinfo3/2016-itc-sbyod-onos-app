package org.sardineproject.sbyod.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.packet.IpAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.rest.AbstractWebResource;
import org.sardineproject.sbyod.cli.completer.DeviceIdCompleter;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;
import org.sardineproject.sbyod.portal.PortalManager;
import org.slf4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Lists User to Service connections
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
    @Path("")
    public Response getServices(){
        log.debug("AppWebUser: Getting all active connections");
        connectionStore = get(ConnectionStore.class);
        Iterator<Connection> connectionIterator = connectionStore.getConnections().iterator();


        ArrayNode arrayNode = mapper().createArrayNode();

        while(connectionIterator.hasNext()) {
            Connection connection = connectionIterator.next();
            // Do not return standard services
            if(connection.getService().name().toString().equals("DnsServiceTcp")
                    || connection.getService().name().toString().equals("DnsServiceUdp")
                    || connection.getService().name().toString().equals("PortalService"))
                continue;

            ObjectNode connectionNode = mapper().createObjectNode();

            // create userNode JSON object
            ObjectNode locationNode = mapper().createObjectNode()
                    .put("elementId", connection.getUser().location().elementId().toString())
                    .put("port", connection.getUser().location().port().toString());
            ArrayNode userIpArray = mapper().createArrayNode();
            for(IpAddress ipAddress : connection.getUser().ipAddresses()) {
                userIpArray.add(ipAddress.toString());
            }
            ObjectNode userNode = (ObjectNode) mapper().createObjectNode()
                    .put("id", connection.getUser().id().toString())
                    .put("mac", connection.getUser().mac().toString())
                    .put("vlan", connection.getUser().vlan().toString())
                    .set("ipAddresses", userIpArray);
            userNode.set("location", locationNode);


            //create serviceNode JSON object
            ArrayNode serviceIpArray = mapper().createArrayNode();
            for(IpAddress ipAddress : connection.getService().ipAddressSet()) {
                serviceIpArray.add(ipAddress.toString());
            }
            ObjectNode serviceNode = (ObjectNode) mapper().createObjectNode()
                    .put("serviceName", connection.getService().name().toString())
                    .put("serviceId", connection.getService().id().toString())
                    .put("serviceTpPort", connection.getService().tpPort().toString())
                    .set("ipAddresses", serviceIpArray);
                    //.put("ip4Address", connection.getService().ipAddressSet().iterator().next().toString());


            //create deviceArray JSON Array (devices with corresponding flow rules for services)
            ArrayNode deviceArray = mapper().createArrayNode();
            //parse to SET to avoid duplicates
            Set<DeviceId> devices = new HashSet<DeviceId>(connection.getForwardingObjectives().values());
            for (DeviceId deviceId : devices){
                ObjectNode deviceNode = mapper().createObjectNode();
                deviceNode.put("deviceId", deviceId.toString());

                ArrayNode flowArray = mapper().createArrayNode();

                for (Map.Entry entry : connection.getForwardingObjectives().entries()) {
                    if (entry.getValue().equals(deviceId)) {
                        flowArray.add(entry.getKey().toString());
                    }
                }
                deviceNode.set("flows", flowArray);
                deviceArray.add(deviceNode);
            }

            //creates the JSON object to return
            connectionNode.set("user", userNode);
            connectionNode.set("service", serviceNode);
            connectionNode.set("devices", deviceArray);

            arrayNode.add(connectionNode);

        }



        JsonNode result = mapper().createObjectNode().set("connections", arrayNode);
        return Response.ok(result).build();
    }

}
