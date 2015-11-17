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
package uni.wue.app;

import jdk.nashorn.internal.objects.Global;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Created by lorry on 13.11.15.
 */
public class PortalManager implements PortalService{


    @Override
    public void setPortal(String portal) {
        checkNotNull(portal, "Portal mac address can not be null");
        checkState(validate(portal), "The portal mac address is not valid");
        //print("Set portal MAC address to %s", portal);
    }

    public boolean validate(String mac) {
        Pattern p = Pattern.compile("^([a-fA-F0-9][:-]){5}[a-fA-F0-9][:-]$");
        Matcher m = p.matcher(mac);
        return m.find();
    }
}
