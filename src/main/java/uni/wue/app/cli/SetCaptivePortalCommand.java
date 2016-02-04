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
    String portalIPv4 = null;

    @Override
    protected void execute() {

        PortalService portalService = get(PortalService.class);
        portalService.setPortal(portalIPv4);
        System.out.println("Set portal IPv4 address to " + portalIPv4);
    }
}
