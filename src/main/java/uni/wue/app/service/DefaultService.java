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
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.AbstractElement;
import org.onosproject.net.Annotations;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;

import java.net.URI;
import java.security.Timestamp;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 10.03.16.
 */
public class DefaultService extends AbstractElement implements Service {

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private final Host host;
    private final TpPort tpPort;
    private final String name;
    private Discovery discovery;

    // for serialization
    private DefaultService(){
        this.host = null;
        this.tpPort = null;
        this.name = null;
    }

    /**
     * Creates a service
     *
     * @param host service host
     * @param tpPort service transport protocol port
     * @param name service name
     * @param providerId identity of the provider
     * @param annotations optional key/value annotations
     */
    public DefaultService(Host host, TpPort tpPort, String name, ProviderId providerId, Annotations... annotations){
        super(providerId, ServiceId.serviceId(URI.create(name + System.currentTimeMillis())), annotations);

        if(host == null || tpPort == null || name == null) {
            log.warn("DefaultService: Illegal Arguments");
            throw new IllegalArgumentException();
        }

        this.host = host;
        this.tpPort = tpPort;
        this.name = name;
        this.discovery = Discovery.NONE;
    }

    /**
     * Creates a service
     *
     * @param host service host
     * @param tpPort service transport protocol port
     * @param name service name
     * @param providerId identity of the provider
     * @param annotations optional key/value annotations
     * @param discovery devine wich service discovery was used
     */
    public DefaultService(Host host, TpPort tpPort, String name, ProviderId providerId, Discovery discovery, Annotations... annotations){
        super(providerId, ServiceId.serviceId(URI.create(name + System.currentTimeMillis())), annotations);

        if(host == null || tpPort == null || name == null) {
            log.warn("DefaultService: Illegal Arguments");
            throw new IllegalArgumentException();
        }

        this.host = host;
        this.tpPort = tpPort;
        this.name = name;
        this.discovery = discovery;
    }

    /**
     * Creates a service
     *
     * @param host service host
     * @param tpPort service transport protocol port
     * @param name service name
     * @param providerId identity of the provider
     * @param annotations optional key/value annotations
     * @param discovery devine wich service discovery was used
     */
    public DefaultService(Host host, TpPort tpPort, String name, ProviderId providerId, String id, Discovery discovery, Annotations... annotations){
        super(providerId, ServiceId.serviceId(URI.create(id)), annotations);

        if(host == null || tpPort == null || name == null) {
            log.warn("DefaultService: Illegal Arguments");
            throw new IllegalArgumentException();
        }

        this.host = host;
        this.tpPort = tpPort;
        this.name = name;
        this.discovery = discovery;
    }

    /**
     * Get the host where the service is running on
     *
     * @return host
     */
    @Override
    public Host getHost() {
        return host;
    }

    /**
     * Get the transport protocol port of the service
     *
     * @return TpPort
     */
    @Override
    public TpPort getTpPort() {
        return tpPort;
    }

    /**
     * Get the discovery tag
     *
     * @return Discovery
     */
    @Override
    public Discovery getServiceDiscovery() {
        return discovery;
    }

    /**
     * Get the name of the service
     *
     * @return name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the network service identifier.
     *
     * @return service identifier
     */
    @Override
    public ServiceId id() {
        return (ServiceId) this.id;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultService that = (DefaultService) o;

        if (host != null ? !host.equals(that.host) : that.host != null) return false;
        return !(tpPort != null ? !tpPort.equals(that.tpPort) : that.tpPort != null);

    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + (tpPort != null ? tpPort.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultService{" +
                "name=" + getName() +
                "hostId=" + host.id() +
                ", tpPort=" + tpPort +
                ", serviceId=" + this.id() +
                '}';
    }
}
