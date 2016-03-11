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
import org.onosproject.net.Host;
import org.slf4j.Logger;
import uni.wue.app.service.Service;

import java.security.InvalidParameterException;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 06.03.16.
 */
public class DefaultConnection implements Connection{

    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private final Host user;
    private final Service service;


    public DefaultConnection(Host user, Service service){

        if(user == null || service == null){
            log.warn("DefaultConnection: Invalid parameter");
            throw new InvalidParameterException(String.format("Invalid parameter in class : {}",
                    this.getClass().toString()));
        }

        this.user = user;
        this.service = service;
    }

    public Service getService() { return service; }

    public Host getUser(){ return user;}


}

