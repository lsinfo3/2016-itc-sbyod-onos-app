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

import java.util.Set;

/**
 * Created by lorry on 10.03.16.
 */
public interface ServiceStore {

    /**
     * Add a new service to the store
     * @param service service to add
     * @return true if service was added to collection
     */
    boolean addService(Service service);

    /**
     * Removes a service from the store and all of the connections with this service.
     *
     * @param service service to remove
     */
    boolean removeService(Service service);

    /**
     * Get all services from the store
     * @return Set of service
     */
    Set<Service> getServices();

    /**
     * Get the service with serviceId
     * @param serviceId ID of the service
     * @return null if service with serviceId not found
     *          else return service
     */
    Service getService(ServiceId serviceId);

    /**
     * Get the services with IP address
     * @param ip4Address of the service
     * @return Set of services
     */
    Set<Service> getService(Ip4Address ip4Address);

    /**
     * Get the services with transport protocol port
     * @param tpPort of the service
     * @return Set of services
     */
    Set<Service> getService(TpPort tpPort);

    /**
     * Get services by name
     * @param name of the service
     * @return set of services
     */
    Set<Service> getService(String name);

    /**
     * ServiceStore already containing the service
     * @param service to check
     * @return true if service is in store
     */
    Boolean contains(Service service);
}
