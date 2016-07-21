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
package org.sardineproject.sbyod.portal;

import com.sun.jersey.core.impl.provider.entity.XMLJAXBElementProvider;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.Host;
import org.sardineproject.sbyod.service.Service;

import java.util.Set;

/**
 * Created by lorry on 13.11.15.
 */
public interface PortalService {

    String APP_ID = "org.sardineproject.sbyod";

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
     * Get the Ip Address of the portal
     *
     * @return IpAddress
     */
    Ip4Address getPortalIp();

    /**
     * Get the portal service
     *
     * @return Service
     */
    Service getPortalService();

}
