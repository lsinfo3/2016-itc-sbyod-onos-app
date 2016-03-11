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
package uni.wue.app.connection;

import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.slf4j.Logger;
import uni.wue.app.service.Service;

import java.security.InvalidParameterException;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
public class DefaultConnection implements Connection{

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private Ip4Address srcIp;
    private MacAddress srcMac;
    private final Service service;


    public DefaultConnection(Ip4Address srcIp, MacAddress srcMac, Service service){

        if(srcIp == null || srcMac == null || service == null){
            log.warn("DefaultConnection: Invalid parameter");
            throw new InvalidParameterException(String.format("Invalid parameter in class : {}",
                    this.getClass().toString()));
        }

        this.srcIp = srcIp;
        this.srcMac = srcMac;
        this.service = service;
    }

    public Ip4Address getSrcIp() {
        return srcIp;
    }

    public MacAddress getSrcMac() {
        return srcMac;
    }

    // TODO: return set of ip addresses
    public Ip4Address getDstIp() {
        return service.getIpv4().iterator().next();
    }

    public TpPort getDstTpPort() {
        return service.getTpPort();
    }

    public Service getService() { return service; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConnection that = (DefaultConnection) o;

        if (srcIp != null ? !srcIp.equals(that.srcIp) : that.srcIp != null) return false;
        if (srcMac != null ? !srcMac.equals(that.srcMac) : that.srcMac != null) return false;
        return !(service != null ? !service.equals(that.service) : that.service != null);

    }

    @Override
    public int hashCode() {
        int result = srcIp != null ? srcIp.hashCode() : 0;
        result = 31 * result + (srcMac != null ? srcMac.hashCode() : 0);
        result = 31 * result + (service != null ? service.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultConnection{" +
                "srcIp=" + srcIp +
                ", srcMac=" + srcMac +
                ", service=" + service +
                '}';
    }
}

