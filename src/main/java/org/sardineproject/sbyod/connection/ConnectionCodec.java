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

import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.Host;
import org.sardineproject.sbyod.service.Service;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 07.03.16.
 *
 * Connection JSON codec.
 */
public final class ConnectionCodec extends JsonCodec<Connection> {

    @Override
    public ObjectNode encode(Connection connection, CodecContext context){
        checkNotNull(connection, "Connection can not be null");

        final JsonCodec<Host> hostCodec = context.codec(Host.class);
        final JsonCodec<Service> serviceCodec = context.codec(Service.class);

        final ObjectNode result = context.mapper().createObjectNode();
        result.set("user", hostCodec.encode(connection.getUser(), context));
        result.set("service", serviceCodec.encode(connection.getService(), context));

        return result;
    }
}
