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

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.store.service.DistributedSet;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by lorry on 14.01.16.
 */
@Component(immediate = true)
@Service
public class AcceptedHostManager implements AcceptedHostService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    protected ApplicationId appId;

    private DistributedSet<Ip4Address> acceptedHosts;

    @Activate
        protected void activate() {
        appId = coreService.registerApplication("uni.wue.app.acceptedHostManager");
        acceptedHosts = storageService.<Ip4Address>setBuilder()
                .withApplicationId(appId)
                .withSerializer(Serializer.using(KryoNamespaces.API))
                .withName("acceptedHosts")
                .build();
        acceptedHosts.clear();

        log.info("Started HostManager");
    }

    @Deactivate
    protected void deactivate() {

        log.info("Stopped HostManager");
    }
    /**
     * Host is allowed to send traffic
     *
     * @param ip4Address of the host
     */
    @Override
    public void allowHost(Ip4Address ip4Address) {
        acceptedHosts.add(ip4Address);
        log.info("Accept host with IPv4: {}",ip4Address);
    }

    /**
     * Host is no longer allowed to send traffic
     *
     * @param ip4Address of the host
     */
    @Override
    public void banHost(Ip4Address ip4Address) {
        if(acceptedHosts.contains(ip4Address)){
            acceptedHosts.remove(ip4Address);
            log.info("Denied access with IPv4: {}",ip4Address);
        } else{
            log.info("Access for {} was already denied");
        }
    }

    /**
     * Get a set of all allowed hosts
     *
     * @return a set of IPv4 addresses
     */
    @Override
    public Set<Ip4Address> getAllowedHosts() {
        Iterator<Ip4Address> it = acceptedHosts.iterator();
        Set<Ip4Address> hostSet = new HashSet<>();
        while(it.hasNext()){
            hostSet.add(it.next());
        }
        return hostSet;
    }

    /**
     * Ask if host is part of accepted hosts
     *
     * @param host to be accepted
     * @return true if host ip is accepted
     */
    @Override
    public Boolean contains(Host host) {
        Set<IpAddress> hostIps = host.ipAddresses();
        for(IpAddress ip : hostIps){
            if(contains(ip.getIp4Address())){
                return true;
            }
        }
        return false;
    }

    /**
     * Ask if host IPv4 is part of accepted hosts
     *
     * @param hostIp to be accepted
     * @return true if host ip is accepted
     */
    @Override
    public Boolean contains(Ip4Address hostIp) {
        return acceptedHosts.contains(hostIp);
    }

}
