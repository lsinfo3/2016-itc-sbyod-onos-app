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
package org.sardineproject.sbyod;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;

import static org.onosproject.net.config.Config.FieldPresence.MANDATORY;

/**
 * Created by lorry on 01.04.16.
 */
public class ByodConfig extends Config<ApplicationId> {

    public static final String PORTAL_IP = "portalIp";
    public static final String PORTAL_PORT = "portalPort";
    public static final String DEFAULT_GATEWAY = "defaultGateway";
    public static final String CONSUL_IP = "consulIp";
    public static final String CONSUL_PORT = "consulPort";


    // TODO: check if config is valid
    @Override
    public boolean isValid(){
        return hasOnlyFields(PORTAL_IP, PORTAL_PORT, DEFAULT_GATEWAY, CONSUL_IP, CONSUL_PORT) &&
                isIpAddress(PORTAL_IP, MANDATORY) &&
                isNumber(PORTAL_PORT, MANDATORY, 1, 10000) &&
                isIpAddress(DEFAULT_GATEWAY, MANDATORY) &&
                isIpAddress(CONSUL_IP, MANDATORY) &&
                isNumber(CONSUL_PORT, MANDATORY, 1, 10000);
    }

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
     * @return transport protocol port integer or -1 if not set
     */
    public int portalPort(){
        return get(PORTAL_PORT, -1);
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

    /**
     * Returns the default gateway ip
     *
     * @return default gateway ip
     */
    public Ip4Address defaultGateway(){
        String defaultGateway = get(DEFAULT_GATEWAY, null);
        return defaultGateway != null ? Ip4Address.valueOf(defaultGateway) : null;
    }

    /**
     * Sets the default gateway ip address
     *
     * @param defaultGateway default gateway ip address; null to clear
     * @return self
     */
    public BasicElementConfig defaultGateway(String defaultGateway){
        return (BasicElementConfig) setOrClear(DEFAULT_GATEWAY, defaultGateway);
    }

    /**
     * Returns the consul ip address
     *
     * @return consul ip address
     */
    public Ip4Address consulIp(){
        String consulIp = get(CONSUL_IP, null);
        return consulIp != null ? Ip4Address.valueOf(consulIp) : null;
    }

    /**
     * Sets the consul ip address
     *
     * @param consulIp consul ip address
     * @return self
     */
    public BasicElementConfig consulIp(String consulIp){
        return (BasicElementConfig) setOrClear(CONSUL_IP,consulIp);
    }

    /**
     * Returns the consul transport protocol port.
     *
     * @return transport protocol port integer or -1 if not set
     */
    public int consulPort(){
        return get(CONSUL_PORT, -1);
    }

    /**
     * Sets the consul port.
     *
     * @param consulPort portalPort new transport protocol port; null to clear
     * @return self
     */
    public BasicElementConfig consulPort(String consulPort){
        return (BasicElementConfig) setOrClear(CONSUL_PORT, consulPort);
    }

}
