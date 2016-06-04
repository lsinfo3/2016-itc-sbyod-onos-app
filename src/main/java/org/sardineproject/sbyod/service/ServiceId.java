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
package org.sardineproject.sbyod.service;

import org.onosproject.net.ElementId;

import java.net.URI;
import java.util.Objects;

/**
 * Created by lorry on 10.03.16.
 */
public class ServiceId extends ElementId {

    private final URI uri;
    private final String str;

    private ServiceId(URI uri){
        this.uri = uri;
        this.str = uri.toString().toLowerCase();
    }

    /**
     * Creates a service id using the supplied URI.
     *
     * @param uri service URI
     * @return ServiceId
     */
    public static ServiceId serviceId(URI uri){
        return new ServiceId(uri);
    }

    /**
     * Creates a service id using the supplied URI string.
     *
     * @param string service URI string
     * @return ServiceId
     */
    public static ServiceId serviceId(String string){
        return serviceId(URI.create(string));
    }

    /**
     * Returns the backing URI.
     *
     * @return backing URI
     */
    public URI uri() {
        return uri;
    }

    @Override
    public int hashCode() {
        return str.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ServiceId) {
            final ServiceId that = (ServiceId) obj;
            return this.getClass() == that.getClass() &&
                    Objects.equals(this.str, that.str);
        }
        return false;
    }

    @Override
    public String toString() {
        return str;
    }

}
