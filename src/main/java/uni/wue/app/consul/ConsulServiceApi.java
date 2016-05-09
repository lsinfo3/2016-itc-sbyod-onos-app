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

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.google.common.net.HostAndPort;
import org.apache.felix.scr.annotations.Component;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;
import org.slf4j.Logger;
import uni.wue.app.service.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by lorry on 19.04.16.
 */
@Component(immediate = true)
@org.apache.felix.scr.annotations.Service
public class ConsulServiceApi implements ConsulService {

    // TODO: update connections if a service changes!
    private static final Logger log = getLogger(uni.wue.app.PortalManager.class);

    private ConsulClient consulClient;
    private Set<Service> consulServices;

    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @param tpPort    transport protocol port
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress, TpPort tpPort) {

        consulClient = new ConsulClient(ipAddress.toString(), tpPort.toInt());

        updateServices();
        return true;
    }

    /**
     * Connect to a running consul agent on TpPort 8500.
     *
     * @param ipAddress Ip address to connect to
     */
    @Override
    public boolean connectConsul(IpAddress ipAddress) {
        return connectConsul(ipAddress, TpPort.tpPort(8500));
    }

    private void updateServices(){
        List<String> datacenters = consulClient.getCatalogDatacenters().getValue();
        QueryParams queryParams = new QueryParams(datacenters.iterator().next());
        Map<String, List<String>> services = consulClient.getCatalogServices(
                queryParams).getValue();

        services.forEach((s,t) -> log.info("" + s + " : " + t));
        services.forEach((s,t) -> log.info(consulClient.getCatalogService(s.toString(), queryParams)
                .getValue().toString()));
    }

}
