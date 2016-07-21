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
package org.sardineproject.sbyod.service;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.Element;
import org.onosproject.net.provider.ProviderId;

/**
 * Created by lorry on 10.03.16.
 */
public interface Service extends Element{

    enum Discovery{CONSUL, NONE}

    /**
     * Get the name of the service
     * @return name
     */
    String name();

    /**
     * Returns the element identifier.
     *
     * @return element id
     */
    @Override
    ServiceId id();

    /**
     * Get the ip address of the service
     *
     * @return a Ip4Address
     */
    Ip4Address ipAddress();

    /**
     * Return the transport protocol of the service
     * @return transport protocol
     */
    byte protocol();

    /**
     * Get the transport protocol port of the service
     * @return TpPort
     */
    TpPort tpPort();

    /**
     * Get the discovery tag
     * @return Discovery
     */
    DefaultService.Discovery serviceDiscovery();

    /**
     * Returning the symbol to display in Portal
     *
     * @return symbol string
     */
    String icon();

    /**
     * A service builder
     */
    interface Builder {

        /**
         * Set the ip address of the service
         *
         * @param ip4Address an IPv4 address
         * @return a service builder
         */
        Builder withIp(Ip4Address ip4Address);

        /**
         * Set the service name
         *
         * @param name a string
         * @return a service builder
         */
        Builder withName(String name);

        /**
         * Set the transport protocol port
         *
         * @param tpPort a transport protocol port
         * @return a service builder
         */
        Builder withPort(TpPort tpPort);

        /**
         * Set the discovery type
         *
         * @param discovery a discovery type
         * @return a service builder
         */
        Builder withDiscovery(Discovery discovery);

        /**
         * Set the service icon from bootstrap glyphicon
         *
         * @param icon a string
         * @return a service builder
         */
        Builder withIcon(String icon);

        /**
         * Set the ipv4 protocol
         *
         * @param protocol a byte
         * @return a service builder
         */
        Builder withProtocol(byte protocol);

        /**
         * Set the provider id
         *
         * @param providerId a provider id
         * @return a service builder
         */
        Builder withProviderId(ProviderId providerId);

        /**
         * Set the element id
         *
         * @param elementId a element id
         * @return a service builder
         */
        Builder withElementId(ElementId elementId);

        /**
         * Builds the service
         *
         * @return a service
         */
        Service build();
    }
}
