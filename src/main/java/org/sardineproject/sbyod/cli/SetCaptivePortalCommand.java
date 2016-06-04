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
package org.sardineproject.sbyod.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.TpPort;
import org.onosproject.cli.AbstractShellCommand;
import org.sardineproject.sbyod.PortalService;

/**
 * Created by lorry on 13.11.15.
 */
@Command(scope="onos", name="set-portal", description = "Define the captive portal")
public class SetCaptivePortalCommand extends AbstractShellCommand{

    @Argument(index=0, name = "portal-ipv4", description = "The host-IPv4 address of the portal",
            required = true, multiValued = false)
    private String portalIPv4 = null;

    @Argument(index=1, name = "portal-TpPort", description = "Port number of switch port the portal is connected to",
            required = true, multiValued = false)
    private String portNumber = null;

    @Override
    protected void execute() {

        PortalService portalService = get(PortalService.class);

        Ip4Address ip4Address;
        TpPort tpPort;
        try{
            ip4Address = Ip4Address.valueOf(portalIPv4);
            tpPort = TpPort.tpPort(Integer.valueOf(portNumber));
        } catch(Exception e){
            System.out.println("Could not set portal. Wrong Argument.");
            return;
        }

        if(portalService.setPortal(ip4Address, tpPort))
            System.out.println(String.format("Set portal with IP %s.", portalIPv4));
        else
            System.out.println(String.format("Could not set portal with IP %s.", portalIPv4));
    }
}
