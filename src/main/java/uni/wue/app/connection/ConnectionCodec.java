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

import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by lorry on 07.03.16.
 *
 * Connection JSON codec.
 */
public final class ConnectionCodec extends JsonCodec<Connection> {

    // JSON fieldNames
    private static final String srcIp = "sourceIP";
    private static final String srcMac = "sourceMAC";
    private static final String dstIp = "destinationIp";
    private static final String dstTpP = "destinationTpPort";

    @Override
    public ObjectNode encode(Connection connection, CodecContext context){
        checkNotNull(connection, "Connection can not be null");
        ConnectionStoreService connectionStore = context.getService(ConnectionStoreService.class);

        ObjectNode result = context.mapper().createObjectNode()
                .put(srcIp, connection.getSrcIp().toString())
                .put(srcMac, connection.getSrcMac().toString())
                .put(dstIp, connection.getDstIp().toString())
                .put(dstTpP, connection.getDstTpPort().toString());

        return result;
    }
}
