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

import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.net.AbstractElement;
import org.onosproject.net.Annotations;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.provider.ProviderId;
import org.sardineproject.sbyod.PortalManager;
import org.slf4j.Logger;

import java.net.URI;

import static org.slf4j.LoggerFactory.getLogger;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 10.03.16.
 */
public class DefaultService extends AbstractElement implements Service {

    private static final Logger log = getLogger(PortalManager.class);

    private final Host host;
    private final Ip4Address ip4Address;
    private final TpPort tpPort;
    private final String name;
    private final Discovery discovery;
    private String icon;
    // fields describing which ip protocol the service has
    // every service is by default tcp
    private byte protocol;


    // for serialization
    private DefaultService(){
        this.host = null;
        this.tpPort = null;
        this.name = null;
        ip4Address = null;
        discovery = null;
    }

    private DefaultService(Builder builder){
        super(builder.providerId, builder.elementId);
        this.ip4Address = builder.ip4Address;
        this.tpPort = builder.tpPort;
        this.name = builder.name;
        this.discovery = builder.discovery;
        this.icon = builder.icon;
        this.protocol = builder.protocol;
        this.host = builder.host;
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
     * Returning the icon to display in Portal
     *
     * @return icon string
     */
    @Override
    public String getIcon() {
        return icon;
    }

    /**
     * Return the transport protocol of the service
     *
     * @return transport protocol
     */
    @Override
    public byte getProtocol() {
        return protocol;
    }

    /**
     * Get the ip address of the service
     *
     * @return a Ip4Address
     */
    @Override
    public Ip4Address ipAddress() {
        return ip4Address;
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
        if (tpPort != null ? !tpPort.equals(that.tpPort) : that.tpPort != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (discovery != that.discovery) return false;
        return !(icon != null ? !icon.equals(that.icon) : that.icon != null);

    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + (tpPort != null ? tpPort.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (discovery != null ? discovery.hashCode() : 0);
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultService{" +
                "host=" + host +
                ", tpPort=" + tpPort +
                ", name='" + name + '\'' +
                ", discovery=" + discovery +
                ", icon='" + icon + '\'' +
                '}';
    }

    /**
     * Returns a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a copy of this service
     *
     * @return a serviceBuilder
     */
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private Ip4Address ip4Address = Ip4Address.valueOf("127.0.0.1");
        private TpPort tpPort;
        private String name;
        private Discovery discovery = Discovery.NONE;
        private String icon = "list";
        private byte protocol = IPv4.PROTOCOL_TCP;
        private ProviderId providerId = ProviderId.NONE;
        private ElementId elementId;
        private Host host;

        // creates an empty builder
        private Builder(){}

        // creates a builder set to create a copy of the specified service.
        private Builder(Service service){
            this.ip4Address = service.ipAddress();
            this.tpPort = service.getTpPort();
            this.name = service.getName();
            this.discovery = service.getServiceDiscovery();
            this.icon = service.getIcon();
            this.protocol = service.getProtocol();
            this.providerId = service.providerId();
            this.elementId = service.id();
            this.host = service.getHost();
        }

        public Builder withIp(Ip4Address ip4Address){
            this.ip4Address = ip4Address;
            return this;
        }

        public Builder withName(String name){
            this.name = name;
            return this;
        }

        public Builder withPort(TpPort tpPort){
            this.tpPort = tpPort;
            return this;
        }

        public Builder withDiscovery(Discovery discovery){
            this.discovery = discovery;
            return this;
        }

        public Builder withIcon(String icon){
            this.icon = icon;
            return this;
        }

        public Builder withProtocol(byte protocol){
            this.protocol = protocol;
            return this;
        }

        public Builder withProviderId(ProviderId providerId){
            this.providerId = providerId;
            return this;
        }

        public Builder withElementId(ElementId elementId){
            this.elementId = elementId;
            return this;
        }

        public Builder withHost(Host host){
            this.host = host;
            return this;
        }

        public Service build(){
            // Todo: use ip address instead of host
            //checkNotNull(ip4Address, "Must have an IP address");
            checkNotNull(tpPort, "Must have an TpPort");
            checkNotNull(name, "Must have a name");
            checkNotNull(host, "Must have a host");

            if(elementId == null){
                elementId = ServiceId.serviceId(URI.create(name + System.currentTimeMillis()));
            }

            return new DefaultService(this);
        }

    }

}
