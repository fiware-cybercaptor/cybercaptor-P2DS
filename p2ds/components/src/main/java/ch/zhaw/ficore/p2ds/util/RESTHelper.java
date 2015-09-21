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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.naming.NamingException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.JAXBException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class RESTHelper {
    public static Object deleteRequest(final String url)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").delete(
                ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new RESTException("deleteRequest failed for: " + url
                    + " got " + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object deleteRequest(final String url,
            final MultivaluedMap<String, String> params)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type(
                MediaType.APPLICATION_FORM_URLENCODED_TYPE).delete(
                ClientResponse.class, params);

        if (response.getStatus() != 200) {
            throw new RESTException("deleteRequest failed for: " + url
                    + " got " + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    /**
     * Deserializes JSON to an object.
     * 
     * @param clazz
     *            Class of the object
     * @param json
     *            the input data
     * @return the object
     * @throws JAXBException
     *             when deseralization fails.
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     */
    public static Object fromJSON(final Class<?> clazz, final String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static HostnameVerifier getHostnameVerifier() {
        return new HostnameVerifier() {

            @Override
            public boolean verify(final String hostname,
                    final javax.net.ssl.SSLSession sslSession) {
                return true;
            }
        };
    }

    public static Object getRequest(final String url)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.get(ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new RESTException("getRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object getRequest(final String url, final Class<?> clazz)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.get(ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new RESTException("getRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(clazz);
    }

    public static Client getSSLClient() {
        ClientConfig config = new DefaultClientConfig();

        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                new HTTPSProperties(getHostnameVerifier(), getSSLContext()));

        config.getClasses().add(JacksonJsonProvider.class);

        return Client.create(config);
    }

    private static SSLContext getSSLContext() { /* this should trust anything... */
        javax.net.ssl.TrustManager x509 = new javax.net.ssl.X509TrustManager() {

            @Override
            public void checkClientTrusted(
                    final java.security.cert.X509Certificate[] arg0,
                    final String arg1)
                    throws java.security.cert.CertificateException {
                return;
            }

            @Override
            public void checkServerTrusted(
                    final java.security.cert.X509Certificate[] arg0,
                    final String arg1)
                    throws java.security.cert.CertificateException {
                return;
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        SSLContext ctx = null;
        try {
            ctx = SSLContext.getInstance("SSL");
            ctx.init(null, new javax.net.ssl.TrustManager[] { x509 }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ctx;
    }

    /*
     * public static void main(final String[] args) throws
     * JsonGenerationException, JsonMappingException, IOException { PeerInfo in
     * = new PeerInfo(); in.setGid(99); String json = toJSON(PeerInfo.class,
     * in); System.out.println("--"); System.out.println(json);
     * System.out.println("--"); PeerInfo pi = (PeerInfo)
     * fromJSON(PeerInfo.class, json); System.out.println(pi);
     * System.out.println(pi.getGid()); }
     */

    public static Object postRequest(final String url)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").post(
                ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object postRequest(final String url, final Class<?> clazz)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").post(
                ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(clazz);
    }

    public static Object postRequest(final String url,
            final MultivaluedMap<String, String> params)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type(
                MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(
                ClientResponse.class, params);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static String postRequest(final String url,
            final MultivaluedMap<String, String> params, final String user,
            final String password) throws ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type(
                MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(
                ClientResponse.class, params);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object postRequest(final String url, final String xml)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").post(
                ClientResponse.class, xml);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    /**
     * Performs a post request and returns the result as an object.
     * 
     * @param url
     *            URL
     * @param xml
     *            XML data to send (will be sent as application/xml).
     * @param clazz
     *            class of the object to be returned (needed for
     *            deserialization)
     * @return the object
     * @throws ClientHandlerException
     *             When a connection error occurs.
     * @throws UniformInterfaceException
     *             When something else went wrong.
     * @throws JAXBException
     *             When either serialization or deserialization fails.
     */
    @SuppressWarnings("rawtypes")
    public static Object postRequest(final String url, final String xml,
            final Class clazz) throws ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").post(
                ClientResponse.class, xml);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return fromJSON(clazz, response.getEntity(String.class));
    }

    public static Object postRequestFile(final String url, final File file,
            final Class<?> clazz) throws IOException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        String txt = "";
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            txt += line;
        }
        br.close();

        ClientResponse response = webResource.type("text/plain").post(
                ClientResponse.class, txt);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(clazz);
    }

    public static Object postRequestJSON(final String url, final String json)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/json").post(
                ClientResponse.class, json);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object postRequestPlain(final String url,
            final MultivaluedMap<String, String> params)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource
                .post(ClientResponse.class, params);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object postRequestPlain(final String url, final String plain,
            final Class clazz) throws ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("text/plain").post(
                ClientResponse.class, plain);

        if (response.getStatus() != 200) {
            throw new RESTException("postRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return fromJSON(clazz, response.getEntity(String.class));
    }

    public static Object putRequest(final String url,
            final MultivaluedMap<String, String> params)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type(
                MediaType.APPLICATION_FORM_URLENCODED_TYPE).put(
                ClientResponse.class, params);

        if (response.getStatus() != 200) {
            throw new RESTException("putRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    public static Object putRequest(final String url, final String xml)
            throws ClientHandlerException, UniformInterfaceException,
            JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").put(
                ClientResponse.class, xml);

        if (response.getStatus() != 200) {
            throw new RESTException("putRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return response.getEntity(String.class);
    }

    @SuppressWarnings("rawtypes")
    public static Object putRequest(final String url, final String xml,
            final Class clazz) throws ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {
        Client client = getSSLClient();

        WebResource webResource = client.resource(url);

        ClientResponse response = webResource.type("application/xml").put(
                ClientResponse.class, xml);

        if (response.getStatus() != 200) {
            throw new RESTException("putRequest failed for: " + url + " got "
                    + response.getStatus() + "|"
                    + response.getEntity(String.class), response.getStatus());
        }

        return fromJSON(clazz, response.getEntity(String.class));
    }

    /**
     * Serializes an object using JAXB to a XML.
     * 
     * @param clazz
     *            Class of the object
     * @param obj
     *            the object
     * @return XML as string
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonGenerationException
     * @throws JAXBException
     *             when serialization fails.
     */
    @SuppressWarnings("rawtypes")
    public static String toJSON(final Class clazz, final Object obj)
            throws JsonGenerationException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(obj);
    }
}
