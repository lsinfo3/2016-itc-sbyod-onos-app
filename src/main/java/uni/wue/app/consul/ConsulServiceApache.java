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
package uni.wue.app.consul;

import java.io.IOException;
import java.net.URI;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.onlab.packet.IpAddress;
import org.onlab.packet.TpPort;

import java.net.URISyntaxException;

/**
 * Created by lorry on 27.04.16.
 */
//@Component(immediate = true)
//@org.apache.felix.scr.annotations.Service
public class ConsulServiceApache implements ConsulService{

    private URI uri;

    @Activate
    protected void activate(){

    }

    @Deactivate
    protected void deactivate(){

    }

    /**
     * Connect to a running consul agent.
     *
     * @param ipAddress Ip address to connect to
     * @param tpPort    transport protocol port
     */
    @Override
    public void connectConsul(IpAddress ipAddress, TpPort tpPort) {
        /*try {
            uri = new URIBuilder()
                    .setScheme("http")
                    .setHost(ipAddress.toString())
                    .setPort(tpPort.toInt())
                    .setPath("/v1/catalog/service/web")
                    .build();

            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(uri);
            CloseableHttpResponse response = httpclient.execute(httpGet);

            try {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    long len = entity.getContentLength();
                    if (len != -1 && len < 2048) {
                        System.out.println(EntityUtils.toString(entity));
                    } else {
                        // Stream content out
                    }
                }
            } finally {
                response.close();
            }

            System.out.println(httpGet);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

    }

    /**
     * Connect to a running consul agent on localhost port 8500.
     */
    @Override
    public void connectConsul() {

    }
}
