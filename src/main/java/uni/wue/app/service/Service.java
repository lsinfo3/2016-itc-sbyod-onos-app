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
package uni.wue.app.service;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.Element;

import java.util.Set;

/**
 * Created by lorry on 10.03.16.
 */
public interface Service extends Element{

    /**
     * Get the host where the service is running on
     * @return host
     */
    Host getHost();

    /**
     * Get the name of the service
     * @return name
     */
    String getName();

    /**
     * Returns the element identifier.
     *
     * @return element id
     */
    @Override
    ServiceId id();

    /**
     * Get the transport protocol port of the service
     * @return TpPort
     */
    TpPort getTpPort();
}
