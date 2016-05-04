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
package uni.wue.app.consul;

import com.google.common.net.HostAndPort;
//import com.orbitz.consul.Consul;
//import com.orbitz.consul.HealthClient;
//import com.orbitz.consul.model.health.ServiceHealth;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;

import java.util.List;

/**
 * Created by lorry on 15.04.16.
 */
//@Component(immediate = true)
//@org.apache.felix.scr.annotations.Service
public class ConsulServiceClient implements ConsulService {

    @Activate
    protected void activate(){

    }

    @Deactivate
    protected void deactivate(){

    }

    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @return true, if connected. False, if not connected
     */
    @Override
    public void connectConsul(IpAddress ipAddress, TpPort tpPort) {
        /*HostAndPort hostAndPort = HostAndPort.fromParts(ipAddress.toString(), tpPort.toInt());
        Consul consul = Consul.builder().build(); // connect to Consul

        HealthClient healthClient = consul.healthClient();
        List<ServiceHealth> nodes = healthClient.getHealthyServiceInstances("DataService").getResponse(); // discover only "passing" nodes

        return;*/
    }

    /**
     * Connect to a running consul agent on localhost port 8500.
     */
    @Override
    public void connectConsul() {
        /*Consul consul = Consul.builder().build(); // connect to Consul

        HealthClient healthClient = consul.healthClient();
        List<ServiceHealth> nodes = healthClient.getHealthyServiceInstances("DataService").getResponse(); // discover only "passing" nodes

        return;*/
    }
}
