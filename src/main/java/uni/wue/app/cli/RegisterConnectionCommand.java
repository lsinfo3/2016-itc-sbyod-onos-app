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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.cli.AbstractShellCommand;
import uni.wue.app.AcceptedHostService;
import uni.wue.app.Connection;
import uni.wue.app.ConnectionStoreService;

/**
 * Created by lorry on 14.01.16.
 */
@Command(scope="onos", name="register-connection", description = "Register a new connection between an user and a service")
public class RegisterConnectionCommand extends AbstractShellCommand{

    @Option(name = "-si", aliases = "--sourceIp", description = "The source IP address of the user",
            required = true, multiValued = false)
    private String srcIp_ = null;

    @Option(name = "-sm", aliases = "--sourceMac", description = "The source MAC address of the user",
            required = true, multiValued = false)
    private String srcMac_ = null;

    @Option(name = "-di", aliases = "--destinationIp", description = "The destination IP address of the service",
            required = true, multiValued = false)
    private String dstIp_ = null;

    @Option(name = "-dp", aliases = "--destinationTpPort", description = "The destination traffic protocol port of the service",
            required = true, multiValued = false)
    private String dstTpPort_ = null;

    private ConnectionStoreService connectionStoreService;

    @Override
    protected void execute() {

        try{
            Ip4Address srcIp = Ip4Address.valueOf(srcIp_);
            MacAddress srcMac = MacAddress.valueOf(srcMac_);
            Ip4Address dstIp = Ip4Address.valueOf(dstIp_);
            TpPort dstTpPort = TpPort.tpPort(Integer.valueOf(dstTpPort_));

            connectionStoreService = get(ConnectionStoreService.class);
            connectionStoreService.addConnection(new Connection(srcIp, srcMac, dstIp, dstTpPort));

            System.out.println(String.format("Added connection between user with IP = {} and MAC = {} " +
                    "and service with IP = {} and TpPort = {}",
                    new String[]{srcIp.toString(), srcMac.toString(), dstIp.toString(), dstTpPort.toString()}));

        } catch (Exception e){
            System.out.println("Could not add connection.");
        }
    }
}
