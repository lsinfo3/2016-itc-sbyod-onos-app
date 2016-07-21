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

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.sardineproject.sbyod.connection.Connection;
import org.sardineproject.sbyod.connection.ConnectionStore;

import java.util.Iterator;

/**
 * Created by lorry on 14.01.16.
 */
@Command(scope="onos", name="list-connections", description = "List the registered connections between user and service")
public class ListConnectionsCommand extends AbstractShellCommand {

    private ConnectionStore connectionStore;

    @Override
    protected void execute() {
        connectionStore = get(ConnectionStore.class);
        Iterator<Connection> connectionIterator = connectionStore.getConnections().iterator();
        print("List of all registered connections:");
        while(connectionIterator.hasNext()){
            Connection connection = connectionIterator.next();
            print("Connection:\n\nuser = " + connection.getUser() + "\nservice = " + connection.getService()
            + "\nFlowObjective count: " + connection.getForwardingObjectives().size());
        }
    }
}