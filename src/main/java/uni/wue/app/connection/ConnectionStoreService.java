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

import java.util.Set;

/**
 * Created by lorry on 06.03.16.
 */
public interface ConnectionStoreService {

    /**
     * Add a new connection to the service
     * @param connection
     */
    void addConnection(Connection connection);

    /**
     * Get the set of connections for source IP and source MAC
     * @param srcIp
     * @param srcMac
     * @return set of connections
     */
    Set<Connection> getConnections(Ip4Address srcIp, MacAddress srcMac);

    /**
     * Get the set of connections for destination IP and destination traffic protocol port
     * @param dstIp
     * @param dstTpPort
     * @return set of connections
     */
    Set<Connection> getConnections(Ip4Address dstIp, TpPort dstTpPort);

    /**
     * Get all registered connections
     * @return set of connections
     */
    Set<Connection> getConnections();

}
