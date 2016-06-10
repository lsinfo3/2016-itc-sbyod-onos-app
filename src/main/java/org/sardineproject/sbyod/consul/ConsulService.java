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
package org.sardineproject.sbyod.consul;

import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;

/**
 * Created by lorry on 15.04.16.
 */
public interface ConsulService {

    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @param tpPort transport protocol port
     */
    boolean connectConsul(IpAddress ipAddress, TpPort tpPort);

    /**
     * Connect to a running consul agent on TpPort 8500.
     * @param ipAddress Ip address to connect to
     */
    boolean connectConsul(IpAddress ipAddress);

    /**
     * Disconnect the running consul agent
     */
    void disconnectConsul();

    /**
     * Get the ip address the consul client is running on
     *
     * @return Ip address
     */
    IpAddress getConsulIp();

    /**
     * Get the transport protocol port the consul client is running on
     *
     * @return transport protocol port
     */
    TpPort getConsulTpPort();

}
