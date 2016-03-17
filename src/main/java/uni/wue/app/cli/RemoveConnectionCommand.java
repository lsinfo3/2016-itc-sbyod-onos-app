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
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.host.HostService;
import uni.wue.app.connection.Connection;
import uni.wue.app.connection.ConnectionStore;
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceId;
import uni.wue.app.service.ServiceStore;

/**
 * Created by lorry on 14.01.16.
 */
@Command(scope="onos", name="remove-connection", description = "Remove an existing connection between an user and a service.")
public class RemoveConnectionCommand extends AbstractShellCommand {

    @Option(name = "-h", aliases = "--hostId", description = "The HostID of the user",
            required = true, multiValued = false)
    private String userId = null;

    @Option(name = "-s", aliases = "--serviceId", description = "The ServiceID of the service",
            required = true, multiValued = false)
    private String serviceId = null;

    private ConnectionStore connectionStore;

    @Override
    protected void execute() {
        try {

            Host user = get(HostService.class).getHost(HostId.hostId(userId));
            Service service = get(ServiceStore.class).getService(ServiceId.serviceId(serviceId));

            connectionStore = get(ConnectionStore.class);
            Connection connection = connectionStore.getConnection(user, service);
            if (connection == null){
                System.out.println(String.format("No connection between user = {} and service = {} found.",
                        user, service));
            }else {
                connectionStore.removeConnection(connection);
                System.out.println(String.format("Connection {} removed", connection));
            }

        } catch (Exception e){
            System.out.println(String.format("Could not remove connection between userId = {} and serviceId = {}",
                    userId, serviceId));
        }
    }
}