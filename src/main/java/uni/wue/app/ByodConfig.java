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
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;

/**
 * Created by lorry on 01.04.16.
 */
public class ByodConfig extends Config<ApplicationId> {

    public static final String PORTAL_IP = "portalIp";
    public static final String PORTAL_PORT = "portalPort";

    /**
     * Returns the portal ip address.
     *
     * @return ip address or null if not set
     */
    public Ip4Address portalIp(){
        String portalIp = get(PORTAL_IP, null);
        return portalIp != null ? Ip4Address.valueOf(portalIp) : null;
    }

    /**
     * Sets the portal ip address.
     *
     * @param portalIp portalIp new ip address; null to clear
     * @return self
     */
    public BasicElementConfig portalIp(String portalIp){
        return (BasicElementConfig) setOrClear(PORTAL_IP, portalIp);
    }

    /**
     * Returns the portal transport protocol port.
     *
     * @return transport protocol port or null if not set
     */
    public TpPort portalPort(){
        String portalPort = get(PORTAL_PORT, null);
        return portalPort != null ? TpPort.tpPort(Integer.valueOf(portalPort)) : null;
    }

    /**
     * Sets the portal port.
     *
     * @param portalPort portalPort new transport protocol port; null to clear
     * @return self
     */
    public BasicElementConfig portalPort(String portalPort){
        return (BasicElementConfig) setOrClear(PORTAL_PORT, portalPort);
    }

}
