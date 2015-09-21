/* Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
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
package ch.zhaw.ficore.p2ds.util;

import java.io.IOError;
import java.net.URLEncoder;
import java.security.Provider;
import java.security.Signature;

import javax.naming.NamingException;
import javax.xml.bind.JAXBException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.OutputStrategy;
import ch.ethz.sepia.mpc.additive.AdditiveMessage;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesMessage;
import ch.ethz.sepia.startup.Configuration;
import ch.zhaw.ficore.p2ds.group.json.PeerInfo;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class SEPIAOutputStrategy implements OutputStrategy {
    private final static XLogger logger = new XLogger(
            LoggerFactory.getLogger(OutputStrategy.class));
    private final String baseUrl;
    private final PeerInfo piInfo;
    private final String recipientId;
    private final String senderId;

    public SEPIAOutputStrategy(final String recipientId, final String senderId) {
        this.recipientId = recipientId;
        this.piInfo = (PeerInfo) Configuration.getInstance(senderId)
                .getPeerInfo(recipientId);
        this.baseUrl = this.piInfo.getUrl();
        this.senderId = senderId;
    }

    private void deliver(final String url, final MultivaluedMapImpl params)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        RESTHelper.postRequestPlain(url, params);
    }

    @Override
    public void send(final Object obj) throws IOError, InterruptedException {

        String data = null;
        String url = null;
        MultivaluedMapImpl params = new MultivaluedMapImpl();

        try {
            /*
             * ByteArrayOutputStream bos = new ByteArrayOutputStream();
             * ObjectOutput out = new ObjectOutputStream(bos);
             * out.writeObject(obj); out.flush(); byte[] rawData =
             * bos.toByteArray(); out.close(); bos.close(); String data =
             * Base64.encodeBase64String(rawData);
             */
            String type = "";
            Class<?> clazz = null;
            if (obj instanceof AdditiveMessage) {
                type = "AdditiveMessage";
                clazz = AdditiveMessage.class;
            } else if (obj instanceof PrimitivesMessage) {
                type = "PrimitivesMessage";
                clazz = PrimitivesMessage.class;
            }

            data = RESTHelper.toJSON(clazz, obj);

            Provider provider = new BouncyCastleProvider();
            Signature dsa = Signature.getInstance("SHA512withECDSA", provider);
            dsa.initSign(Configuration.getInstance(this.senderId)
                    .getPrivateKey());
            dsa.update(data.getBytes("UTF-8"));
            byte[] sig = dsa.sign();
            String signature = Certificates.encodeBase64(sig);

            params.add("data", data);
            params.add("signature", signature);

            url = this.baseUrl + "/message/"
                    + URLEncoder.encode(this.recipientId, "UTF-8") + "/"
                    + URLEncoder.encode(this.senderId, "UTF-8") + "/" + type;

            deliver(url, params);
        } catch (RESTException e) {
            /* Let's try to re-deliver it */
            if (url != null && data != null) {
                try {
                    logger.info("Waiting...");
                    Thread.sleep(10000);
                    logger.info("Try to redeliver!");
                    deliver(url, params);
                } catch (ClientHandlerException | UniformInterfaceException
                        | JAXBException | NamingException e1) {
                    logger.catching(e);
                    throw new IOError(e);
                }
            } else {
                throw new IOError(e);
            }
        } catch (Exception e) {
            logger.catching(e);
            logger.error("could not send");
            throw new IOError(e);
        }

    }
}
