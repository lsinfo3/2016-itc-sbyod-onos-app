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
import org.onosproject.cli.AbstractShellCommand;
import uni.wue.app.PortalManager;
import uni.wue.app.PortalService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lorry on 13.11.15.
 */
@Command(scope="onos", name="set-portal", description = "Define the captive portal")
public class SetCaptivePortalCommand extends AbstractShellCommand{

    @Argument(index=0, name = "portal-ipv4", description = "The host-IPv4 address of the portal",
            required = true, multiValued = false)
    private String portalIPv4 = null;

    @Option(name = "-m", aliases = "--mac", description = "The host MAC address of the portal",
            required = false, multiValued = false)
    private String portalMac = null;

    @Option(name = "-s", aliases = "--switch", description = "The device ID of the connected switch",
            required = false, multiValued = false)
    private String deviceId = null;

    @Option(name = "-p", aliases = "--port", description = "Port number of switch port the portal is connected to",
            required = false, multiValued = false)
    private String portNumber = null;

    @Override
    protected void execute() {

        PortalService portalService = get(PortalService.class);

        boolean success;
        if(portalMac != null && deviceId != null && portNumber != null)
            success = portalService.setPortal(portalIPv4, portalMac, deviceId, portNumber);
        else
            success = portalService.setPortal(portalIPv4);

        if(success)
            System.out.println(String.format("Set portal with IP %s.", portalIPv4));
        else
            System.out.println(String.format("Could not set portal with IP %s.\n" +
                    "Try defining a new one giving the mac address, connected device and port number.", portalIPv4));
    }
}
