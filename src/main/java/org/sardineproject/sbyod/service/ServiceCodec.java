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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 10.03.16.
 */
public class ServiceCodec extends JsonCodec<Service> {

    // JSON field names
    private static final String name = "serviceName";
    private static final String id = "serviceId";
    private static final String tpPort = "serviceTpPort";
    private static final String icon = "icon";

    @Override
    public ObjectNode encode(Service service, CodecContext context){
        checkNotNull(service, "Service can not be null");

        return context.mapper().createObjectNode()
                .put(name, service.name())
                .put(id, service.id().toString())
                .put(tpPort, (service.tpPort() == null ? "" : service.tpPort().toString()))
                .put(icon, service.icon());
    }
}
