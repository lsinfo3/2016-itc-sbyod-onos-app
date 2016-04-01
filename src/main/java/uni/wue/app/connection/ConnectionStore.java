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
package uni.wue.app.connection;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;
import uni.wue.app.service.Service;

import java.util.Set;

/**
 * Created by lorry on 06.03.16.
 */
public interface ConnectionStore {

    /**
     * Add a new connection to the service
     *
     * @param connection
     */
    void addConnection(Connection connection);

    /**
     * Removes the connection between user and service
     *
     * @param connection
     */
    void removeConnection(Connection connection);

    /**
     * Get the set of connection for source IP address
     *
     * @param srcIp IPv4 address of the user
     * @return set of connections
     */
    Set<Connection> getUserConnections(Ip4Address srcIp);

    /**
     * Get the set of connections for source IP and source MAC
     *
     * @param srcIp
     * @param srcMac
     * @return set of connections
     */
    Set<Connection> getUserConnections(Ip4Address srcIp, MacAddress srcMac);

    /**
     * Get the set of connections for destination IP and destination traffic protocol port
     *
     * @param dstIp
     * @param dstTpPort
     * @return set of connections
     */
    Set<Connection> getServiceConnections(Ip4Address dstIp, TpPort dstTpPort);

    /**
     * Get the connection between the user and the service
     *
     * @param user
     * @param service
     * @return connection between user and service
     */
    Connection getConnection(Host user, Service service);

    /**
     * Get the connections of a service
     *
     * @param service
     * @return Set of connections
     */
    Set<Connection> getConnections(Service service);

    /**
     * Get all registered connections
     *
     * @return set of connections
     */
    Set<Connection> getConnections();

    /**
     * Ask if connection is already installed
     *
     * @param connection the connection to check
     * @return true if connection already installed
     */
    Boolean contains(Connection connection);

}
