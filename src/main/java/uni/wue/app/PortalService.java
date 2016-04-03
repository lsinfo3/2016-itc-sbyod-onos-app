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
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.Host;
import uni.wue.app.service.Service;

import java.util.Set;

/**
 * Created by lorry on 13.11.15.
 */
public interface PortalService {

    /**
     * Set the captive portal
     *
     * @param portalIp Ip address of the portal
     * @param portalTpPort Transport protocol port of the portal
     *
     * @return true if the portal was set correctly
     */
    boolean setPortal(Ip4Address portalIp, TpPort portalTpPort);

    /**
     * Get the Mac Address of the portal
     *
     * @return MacAddress
     */
    MacAddress getPortalMac();

    /**
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    Set<IpAddress> getPortalIp();

    /**
     * Get the portal as host
     *
     * @return Host
     */
    Host getPortal();

    /**
     * Get the portal service
     *
     * @return Service
     */
    Service getPortalService();

}
