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
package org.sardineproject.sbyod.connection;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.sardineproject.sbyod.portal.PortalManager;
import org.slf4j.Logger;
import org.sardineproject.sbyod.service.Service;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
public class DefaultConnection implements Connection{

    private static final Logger log = getLogger(PortalManager.class);

    private final Host user;
    private final Service service;
    private ArrayListMultimap<ForwardingObjective, DeviceId> forwardingObjectives;


    public DefaultConnection(Host user, Service service){

        if(user == null || service == null){
            log.warn("DefaultConnection: Invalid parameter");
            throw new InvalidParameterException(String.format("Invalid parameter in class : %s",
                    this.getClass().toString()));
        }

        // check if the user holds any of the IP addresses of the service
        Set<IpAddress> intersection = Sets.newHashSet(user.ipAddresses());
        intersection.retainAll(service.ipAddressSet());
        if(!intersection.isEmpty()){
            log.warn("DefaultConnection: Creating a connection for a host, where the service is running on," +
                    " is forbidden.\nHost={}\nService={}", user.id(), service);
            throw new InvalidParameterException(String.format("Invalid parameter in class : %s",
                    this.getClass().toString()));
        }

        this.user = user;
        this.service = service;
        forwardingObjectives = ArrayListMultimap.create();
    }

    public Service getService() { return service; }

    public Host getUser(){ return user;}

    /**
     * Add an objective to remove the installed flow rules realising the connection
     *
     * @param forwardingObjective forwarding objective to remove the installed flow rules
     * @param deviceId the device id the objective is running on
     */
    @Override
    public void addForwardingObjective(ForwardingObjective forwardingObjective, DeviceId deviceId) {
        if(forwardingObjective == null)
            return;
        forwardingObjectives.put(forwardingObjective, deviceId);
    }

    /**
     * Returns all forwarding objectives removing the installed objectives
     *
     * @return forwarding objective
     */
    @Override
    public ArrayListMultimap<ForwardingObjective, DeviceId> getForwardingObjectives() {
        return ArrayListMultimap.create(forwardingObjectives);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConnection that = (DefaultConnection) o;

        if (!user.equals(that.user)) return false;
        return service.equals(that.service);

    }

    @Override
    public int hashCode() {
        int result = user.hashCode();
        result = 31 * result + service.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DefaultConnection{" +
                "user=" + user +
                ", service=" + service +
                '}';

    }
}

