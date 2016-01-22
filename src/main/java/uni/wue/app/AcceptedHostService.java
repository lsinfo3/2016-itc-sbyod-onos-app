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
import org.onosproject.net.Host;

import java.util.Set;

/**
 * Created by lorry on 14.01.16.
 */
public interface AcceptedHostService {

    /**
     * Host is allowed to send traffic
     * @param ip4Address of the host
     */
    void allowHost(Ip4Address ip4Address);

    /**
     * Host is no longer allowed to send traffic
     * @param ip4Address of the host
     */
    void banHost(Ip4Address ip4Address);

    /**
     * Get a set of all allowed hosts
     * @return a set of IPv4 addresses
     */
    Set<Ip4Address> getAllowedHosts();

    /**
     * Ask if host is part of accepted hosts
     * @param host to be accepted
     * @return true if host ip is accepted
     */
    Boolean contains(Host host);

    /**
     * Ask if host IPv4 is part of accepted hosts
     * @param hostIp to be accepted
     * @return true if host ip is accepted
     */
    Boolean contains(Ip4Address hostIp);
}
