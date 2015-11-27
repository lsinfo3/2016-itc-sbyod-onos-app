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
import org.onosproject.cli.AbstractShellCommand;
import uni.wue.app.PortalService;

/**
 * Created by lorry on 27.11.15.
 */
@Command(scope="onos", name="get-portal", description = "Show portal information")
public class GetCaptivePortalCommand extends AbstractShellCommand {
    @Override
    protected void execute() {
        PortalService portalService = get(PortalService.class);
        System.out.println(String.format("Portal Mac address: %s\nPortal Ip address: %s",
                portalService.getPortalMac(), portalService.getPortalIp()));
    }
}
