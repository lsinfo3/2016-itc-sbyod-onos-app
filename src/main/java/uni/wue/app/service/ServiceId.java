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

import org.onlab.packet.IpAddress;
import org.onosproject.net.ElementId;

import java.util.Set;

/**
 * Created by lorry on 10.03.16.
 */
public class ServiceId extends ElementId {

    private final String name;
    private final Set<IpAddress> ipAddresses;

    private ServiceId(String name, Set<IpAddress> ip4Address){
        this.name = name;
        this.ipAddresses = ip4Address;
    }

    public static ServiceId serviceId(String name, Set<IpAddress> ip4Address){
        return new ServiceId(name, ip4Address);
    }

    public String name(){
        return name;
    }

    public Set<IpAddress> ip4Address(){
        return ipAddresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceId serviceId = (ServiceId) o;

        if (name != null ? !name.equals(serviceId.name) : serviceId.name != null) return false;
        return !(ipAddresses != null ? !ipAddresses.equals(serviceId.ipAddresses) : serviceId.ipAddresses != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (ipAddresses != null ? ipAddresses.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServiceId{" +
                "name='" + name + '\'' +
                ", ipAddresses=" + ipAddresses +
                '}';
    }
}
