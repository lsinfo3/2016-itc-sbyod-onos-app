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
import uni.wue.app.service.Service;
import uni.wue.app.service.ServiceStore;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by lorry on 10.03.16.
 */
@Command(scope="onos", name="list-services", description = "List the registered services")
public class ListServicesCommand extends AbstractShellCommand{
    /**
     * Executes this command.
     */
    @Override
    protected void execute() {
        Iterator<Service> serviceIterator = get(ServiceStore.class).getServices().iterator();
        print("Services:");
        while(serviceIterator.hasNext())
            print(serviceIterator.next().toString());
    }
}
