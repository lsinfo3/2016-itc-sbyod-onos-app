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
package uni.wue.app.cli;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.host.HostService;
import uni.wue.app.connection.DefaultConnection;
import uni.wue.app.connection.ConnectionStoreService;
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceStore;

/**
 * Created by lorry on 14.01.16.
 */
@Command(scope="onos", name="register-connection", description = "Register a new connection between an user and a service")
public class RegisterConnectionCommand extends AbstractShellCommand{

    @Option(name = "-s", aliases = "--sourceIp", description = "The source IP address of the user",
            required = true, multiValued = false)
    private String srcIp_ = null;

    @Option(name = "-d", aliases = "--serviceName", description = "The name of the destination service",
            required = true, multiValued = false)
    private String dstService = null;

    private ConnectionStoreService connectionStoreService;

    @Override
    protected void execute() {

        try{
            Ip4Address srcIp = Ip4Address.valueOf(srcIp_);
            MacAddress srcMac = get(HostService.class).getHostsByIp(srcIp).iterator().next().mac();
            Service service = get(ServiceStore.class).getService(dstService).iterator().next();

            connectionStoreService = get(ConnectionStoreService.class);
            connectionStoreService.addConnection(new DefaultConnection(srcIp, srcMac, service));

            System.out.println(String.format("Added connection between user with IP = {} and MAC = {} " +
                    "and service with name = {}",
                    new String[]{srcIp.toString(), srcMac.toString(), service.toString()}));

        } catch (Exception e){
            System.out.println("Could not add connection.");
        }
    }
}
