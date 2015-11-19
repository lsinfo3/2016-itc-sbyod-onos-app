/*
 * Copyright 2014 Open Networking Laboratory
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
 */
package uni.wue.app;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onosproject.TestApplicationId;
import org.onosproject.core.IdGenerator;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.MockIdGenerator;

/**
 * Set of tests of the ONOS application manager.
 */
public class PortalManagerTest {

    protected PortalManager manager;
    protected IdGenerator idGenerator = new MockIdGenerator();

    @Before
    public void setUp() {
        manager = new PortalManager();
        manager.appId = new TestApplicationId("network-test");
        Intent.bindIdGenerator(idGenerator);

    }

    @After
    public void tearDown() {
        Intent.unbindIdGenerator(idGenerator);
    }

    @Test
    public void basics() {

    }

}
