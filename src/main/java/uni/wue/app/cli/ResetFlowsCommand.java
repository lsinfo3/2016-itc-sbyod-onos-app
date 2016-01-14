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
import uni.wue.app.FlowRedirect;
import uni.wue.app.PacketRedirectService;

/**
 * Created by lorry on 08.01.16.
 */
@Command(scope="onos", name="reset-portal-flows", description = "Remove the flows leading from and to the captive portal.")
public class ResetFlowsCommand extends AbstractShellCommand {

    @Override
    protected void execute() {
        PacketRedirectService packetRedirect = get(PacketRedirectService.class);
        if(packetRedirect.getClass().equals(FlowRedirect.class)) {
            ((FlowRedirect) packetRedirect).removeFlows();
            System.out.println("Removed portal flows");
        }
    }
}
