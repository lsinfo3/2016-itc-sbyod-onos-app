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
package uni.wue.app;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;

/**
 * Created by lorry on 01.03.16.
 */
public interface HostConnectionService {

    /**
     * Establish a connection between the user with userIp and userMac and a service with serviceIp and servicePort
     * @param userIp the IP address of the user
     * @param userMac the MAC address of the user
     * @param serviceIp the IP address of the service
     * @param servicePort the port of the service
     */
    void addConnection(Ip4Address userIp, MacAddress userMac, Ip4Address serviceIp, TpPort servicePort);

}
