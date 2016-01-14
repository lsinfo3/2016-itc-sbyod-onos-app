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
import org.onlab.packet.Ip4Address;
import org.onosproject.cli.AbstractShellCommand;
import uni.wue.app.AcceptedHostService;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by lorry on 14.01.16.
 */
@Command(scope="onos", name="accepted-hosts", description = "List the accepted hosts")
public class ListAcceptedHostsCommand extends AbstractShellCommand {

    private AcceptedHostService acceptedHostService;

    @Override
    protected void execute() {
        acceptedHostService = get(AcceptedHostService.class);
        Set<Ip4Address> allowedHosts = acceptedHostService.getAllowedHosts();
        Iterator<Ip4Address> it = acceptedHostService.getAllowedHosts().iterator();
        print("All hosts with following IP are allowed to send traffic:");
        while(it.hasNext()){
            print("Host-IPv4: " + it.next().toString());
        }
    }
}