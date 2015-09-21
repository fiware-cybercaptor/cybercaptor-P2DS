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
package ch.zhaw.ficore.p2ds.peer;

import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.mpc.additive.AdditiveMessage;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesMessage;
import ch.ethz.sepia.startup.Configuration;
import ch.zhaw.ficore.p2ds.group.json.DataSet;
import ch.zhaw.ficore.p2ds.group.json.DataSets;
import ch.zhaw.ficore.p2ds.group.json.GroupConfigurationInfo;
import ch.zhaw.ficore.p2ds.group.json.GroupInfo;
import ch.zhaw.ficore.p2ds.group.json.PeerInfo;
import ch.zhaw.ficore.p2ds.peer.storage.Manager;
import ch.zhaw.ficore.p2ds.peer.storage.PeerConfiguration;
import ch.zhaw.ficore.p2ds.peers.json.PeerConfigurationInfo;
import ch.zhaw.ficore.p2ds.util.Certificates;
import ch.zhaw.ficore.p2ds.util.RESTHelper;
import ch.zhaw.ficore.p2ds.util.SEPIALauncher;

@Path("/")
public class PeerService {

    private final static String ERR_INV_ADMIN_KEY = "ERR_INV_ADMIN_KEY";

    private final static String ERR_INV_DATA = "ERR_INV_DATA";
    private final static String ERR_INV_REG_CODE = "ERR_INV_REG_CODE";
    private final static String ERR_INV_SIGNATURE = "ERR_INV_SIGNATURE";
    private final static String ERR_NO_KEY = "ERR_NO_KEY";
    private final static String ERR_NO_PEER = "ERR_NO_PEER";
    private final static String ERR_NOT_FOUND = "ERR_NOT_FOUND";
    private final static String ERR_NOT_RUNNING = "ERR_NOT_RUNNING";
    private final static String ERR_RUNNING = "ERR_RUNNING";
    private final static String ERR_SERVER = "ERR_SERVER";
    private final static String ERR_SUCCESS = "SUCCESS";

    /** The logger. */
    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(PeerService.class));

    private final static Map<String, String> registrationCodes = new HashMap<String, String>();
    private final static Map<String, SEPIALauncher> sepiaInstances = new HashMap<String, SEPIALauncher>();
    private String adminKey = null;

    /**
     * Constructor that basically just reads the peer/adminKey from the web.xml
     * 
     * @throws Exception
     *             If initial context can not be created.
     */
    public PeerService() throws Exception {
        LOGGER.info("New PeerService Instance");

        InitialContext ic = new InitialContext();

        try {
            this.adminKey = (String) ic.lookup("java:/comp/env/peer/adminKey");
        } catch (Exception ex) {
            LOGGER.info("using default-admin-key. (Not good if you see this in production!)");
            this.adminKey = "default-admin-key";
        }

    }

    /**
     * POST /input: Takes a DataSet and adds it to the input queue of the target
     * peer. The peer must be running otherwise the method will return with
     * ERR_NOT_RUNNING. Registration code must match the registration code of
     * the peer as configured in its configuration. If the format of the input
     * data is incorrect this method will return ERR_INV_DATA. Consumes
     * application/json.
     * 
     * @param inputData
     *            DataSet containing the input values
     * @param registrationCode
     *            Registration code
     * @return (text/plain) OK or ERR_*
     */
    @POST
    @Path("/input")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response addInput(final DataSet inputData,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(inputData);
        try {

            if (!verifyRegistrationCode(inputData.getPeerName(),
                    registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_INV_REG_CODE)
                        .build());
            }

            synchronized (sepiaInstances) {
                SEPIALauncher sepiaInstance = sepiaInstances.get(inputData
                        .getPeerName());
                if (sepiaInstance == null) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_NOT_RUNNING)
                            .build());
                }

                if (!isValidCSVData(inputData.getData())) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_INV_DATA)
                            .build());
                }

                sepiaInstance.addInputData(inputData.getData());
            }

            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SERVER).build());
        }
    }

    /**
     * POST /inputs: Takes multiple data sets that are added to the input queue
     * of the target peer. The peer must be running otherwise the method will
     * return with ERR_NOT_RUNNING. If the format of the input data is not
     * correct this method will return with ERR_INV_DATA. Consumes
     * application/json.
     * 
     * @param dataSets
     *            A list of DataSet.
     * @param registrationCode
     *            registration code
     * @return (text/plain) OK or ERROR.
     */
    @POST
    @Path("/inputs")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response addInputs(final DataSets dataSets,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(dataSets);
        try {

            if (!verifyRegistrationCode(dataSets.getPeerName(),
                    registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_INV_REG_CODE)
                        .build());
            }

            synchronized (sepiaInstances) {
                SEPIALauncher sepiaInstance = sepiaInstances.get(dataSets
                        .getPeerName());
                if (sepiaInstance == null) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_NOT_RUNNING)
                            .build());
                }

                for (String data : dataSets.getData()) {

                    if (!isValidCSVData(data)) {
                        return LOGGER.exit(Response.status(400)
                                .type(MediaType.TEXT_PLAIN)
                                .entity(ERR_INV_DATA).build());
                    }

                    sepiaInstance.addInputData(data);
                }
            }

            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SERVER).build());
        }
    }

    /**
     * DELETE /peer/{peerName}: Delete a peer. This will delete a peer. Will
     * return ERR_NO_PEER if peer does not exist.
     * 
     * @param peerName
     *            Name of the peer to delete.
     * @param adminKey
     *            Admin key
     * @return (text/plain) OK or ERROR.
     */
    @DELETE()
    @Path("/peer/{peerName}")
    public Response deletePeerConfiguration(
            @PathParam("peerName") final String peerName,
            @QueryParam("adminKey") final String adminKey) {
        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();

            PeerConfiguration pc = PeerConfiguration.findRaw(peerName);
            if (pc == null) {
                return LOGGER
                        .exit(Response.status(404).type(MediaType.TEXT_PLAIN)
                                .entity(ERR_NO_PEER).build());
            }

            em.remove(pc);
            et.commit();

            return LOGGER.exit(Response.status(200).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SUCCESS).build());
        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * GET /peer/{peerName}: Retrieve the configuration of a peer as it was
     * registered on this service.
     * 
     * @param peerName
     *            Name of the peer
     * @return (application/json) PeerConfigurationInfo
     */
    @GET()
    @Path("/peer/{peerName}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getPeerConfiguration(
            @PathParam("peerName") final String peerName,
            @QueryParam("adminKey") final String adminKey) {
        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        PeerConfigurationInfo pci = PeerConfiguration.find(peerName);

        if (pci == null) {
            return LOGGER.exit(Response.status(404).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_NO_PEER).build());
        }

        return LOGGER
                .exit(Response.ok(pci, MediaType.APPLICATION_JSON).build());
    }

    /**
     * Checks that input data has the correct CSV-format.
     * 
     * @param data
     *            Input string
     * @return true or false
     */
    private boolean isValidCSVData(final String data) {
        LOGGER.entry(data);
        return LOGGER.exit(data.matches("[0-9\\s;]*"));
    }

    /**
     * POST /message/{recipient}/{sender}/{type}: This method is used to deliver
     * messages to peers hosted on this service. The data sent to this method
     * must have been signed by the sender so that the recipient can verify the
     * integrity and source of the message. The signature algorithm used is
     * SHA512withECDSA. Messages are only deliverable to peers having a running
     * MPC instance. Additionally the type of the message must be specified.
     * Currently the following types are supported: AdditiveMessage,
     * PrimitivesMessage. This method responds with (500, ERR_INV_SIGNATURE) if
     * the signature check fails.
     * 
     * @param data
     *            base64 encoded message
     * @param recipient
     *            name of the recipient
     * @param sender
     *            name of the sender
     * @param type
     *            type of the message
     * @param signature
     *            signature
     * @return (text/plain) OK or ERROR
     */
    @POST()
    @Path("/message/{recipient}/{sender}/{type}")
    public Response message(@FormParam("data") final String data,
            @PathParam("recipient") final String recipient,
            @PathParam("sender") final String sender,
            @PathParam("type") final String type,
            @FormParam("signature") final String signature) {
        LOGGER.entry(data, recipient, sender);

        try {
            LOGGER.info("Message for " + recipient + " from " + sender
                    + " with data " + data);

            if (!Configuration.hasInstance(recipient)) {
                return LOGGER.exit(Response.status(404)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_NOT_FOUND)
                        .build());
            }

            if (Configuration.getInstance(recipient).getPublicKey(sender) == null) {
                return LOGGER.exit(Response.status(404)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_NO_KEY).build());
            }

            Provider provider = new BouncyCastleProvider();
            Signature sig = Signature.getInstance("SHA512withECDSA", provider);
            sig.initVerify(Configuration.getInstance(recipient).getPublicKey(
                    sender));
            sig.update(data.getBytes("UTF-8"));
            boolean verifies = sig.verify(Certificates.decodeBase64(signature));

            if (verifies) {
                LOGGER.info("Signature ok!");
            } else {
                return LOGGER.exit(Response.status(500)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_INV_SIGNATURE)
                        .build());
            }

            Class<?> clazz = null;

            if (type.equals("AdditiveMessage")) {
                clazz = AdditiveMessage.class;
            } else if (type.equals("PrimitivesMessage")) {
                clazz = PrimitivesMessage.class;
            }

            Object obj = RESTHelper.fromJSON(clazz, data);

            ConnectionManager cm = Configuration.getInstance(recipient)
                    .getConnectionManager();
            BlockingQueue<Object> input = cm.getInputQueue(sender);
            input.put(obj);

            return LOGGER.exit(Response.status(200).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SUCCESS).build());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SERVER).build());
        }
    }

    /**
     * Register the peer at the group management service. Was initially exposed
     * but can now only be called internally. This method will read peer/url
     * from the web.xml.
     * 
     * @param peerName
     *            name of the peer
     * @param registrationCode
     *            registration code
     * @param peerType
     *            type of the peer
     * @return Response
     */
    private Response register(final String peerName,
            final String registrationCode, final int peerType) {
        LOGGER.entry();

        try {
            if (!verifyRegistrationCode(peerName, registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_INV_REG_CODE)
                        .build());
            }

            PeerConfigurationInfo pci = PeerConfiguration.find(peerName);

            if (pci == null) {
                return LOGGER
                        .exit(Response.status(404).type(MediaType.TEXT_PLAIN)
                                .entity(ERR_NO_PEER).build());
            }

            InitialContext ic = new InitialContext();

            String peerURL = (String) ic.lookup("java:/comp/env/peer/url");

            String name = peerName;
            String regCode = pci.getRegistrationCode();

            regCode = URLEncoder.encode(regCode, "UTF-8");

            if (!regCode.equals(registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .entity(ERR_INV_REG_CODE).build());
            }

            String baseURL = pci.getGroupMgmtURL();
            String url = "/register/" + regCode + "?url="
                    + URLEncoder.encode(peerURL, "UTF-8") + "&name=" + name
                    + "&type=" + peerType;
            PeerInfo pi = (PeerInfo) RESTHelper.postRequest(baseURL + url,
                    PeerInfo.class);

            String pubKey = pci.getPublicKey();

            pi = (PeerInfo) RESTHelper.postRequestPlain(baseURL + "/publicKey/"
                    + URLEncoder.encode(peerName, "UTF-8")
                    + "?registrationCode=" + regCode, pubKey, PeerInfo.class);

            return LOGGER.exit(Response.ok(pi, MediaType.APPLICATION_JSON)
                    .build());

        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SERVER).build());
        }
    }

    /**
     * POST /start/{peerName}: Starts the target peer.
     * 
     * @param peerName
     *            Name of the peer
     * @param registrationCode
     *            Registration code
     * @return (application/json) PeerInfo
     */
    @POST()
    @Path("/start/{peerName}")
    public Response start(@PathParam("peerName") final String peerName,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(peerName, registrationCode);
        LOGGER.info("STARTPEER");

        synchronized (sepiaInstances) {
            try {

                if (!verifyRegistrationCode(peerName, registrationCode)) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN)
                            .entity(ERR_INV_REG_CODE).build());
                }

                if (sepiaInstances.get(peerName) != null) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_RUNNING)
                            .build());
                }

                PeerConfigurationInfo pci = PeerConfiguration.find(peerName);

                if (pci == null) {
                    return LOGGER.exit(Response.status(404)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_NO_PEER)
                            .build());
                }

                URLEncoder.encode(peerName, "UTF-8");

                String baseURL = pci.getGroupMgmtURL();

                String url = baseURL + "/groupInfo/"
                        + URLEncoder.encode(peerName, "UTF-8")
                        + "?registrationCode="
                        + URLEncoder.encode(registrationCode, "UTF-8");

                GroupInfo gi = (GroupInfo) RESTHelper.getRequest(url,
                        GroupInfo.class);

                url = baseURL + "/configuration/"
                        + URLEncoder.encode(peerName, "UTF-8")
                        + "?registrationCode="
                        + URLEncoder.encode(registrationCode, "UTF-8");

                GroupConfigurationInfo gc = (GroupConfigurationInfo) RESTHelper
                        .getRequest(url, GroupConfigurationInfo.class);

                PeerInfo myPeerInfo = null;
                for (PeerInfo piGroup : gi.getPeers()) {
                    if (peerName.equals(piGroup.getPeerName())) {
                        myPeerInfo = piGroup;
                    }
                }
                if (myPeerInfo == null || gc == null
                        || !myPeerInfo.getPeerName().equals(peerName)) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_INV_DATA)
                            .build());
                }

                byte[] encKey = Certificates.decodeBase64(pci.getPrivateKey());

                PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(
                        encKey);
                Provider provider = new BouncyCastleProvider();
                KeyFactory keyFactory = KeyFactory.getInstance("EC", provider);
                PrivateKey pk = keyFactory.generatePrivate(privKeySpec);

                sepiaInstances.put(
                        peerName,
                        new SEPIALauncher(myPeerInfo, gi, gc, sepiaInstances,
                                registrationCode, baseURL, pk, pci
                                        .getFinalResultsURL()));

                return LOGGER.exit(Response.ok(myPeerInfo,
                        MediaType.APPLICATION_JSON).build());

            } catch (Exception e) {
                LOGGER.catching(e);
                return LOGGER.exit(Response.status(500)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_SERVER).build());
            }
        }
    }

    /**
     * GET /status: Just a method to see if the service is running.
     * 
     * @return (text/plain) OK
     */
    @GET()
    @Path("/status")
    @Produces({ MediaType.TEXT_PLAIN })
    public Response status() {
        Manager.getEntityManager().close();
        return Response.ok("OK").build();
    }

    /**
     * POST /stop/{peerName}: Stops the target peer.
     * 
     * @param peerName
     *            Name of the peer
     * @param registrationCode
     *            Registration code
     * @return (text/plain) OK or ERROR.
     */
    @POST()
    @Path("/stop/{peerName}")
    public Response stop(@PathParam("peerName") final String peerName,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(peerName, registrationCode);
        LOGGER.info("STOPPEER");

        synchronized (sepiaInstances) {
            try {

                if (!verifyRegistrationCode(peerName, registrationCode)) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN)
                            .entity(ERR_INV_REG_CODE).build());
                }

                if (sepiaInstances.get(peerName) == null) {
                    return LOGGER.exit(Response.status(400)
                            .type(MediaType.TEXT_PLAIN).entity(ERR_NOT_RUNNING)
                            .build());
                }

                sepiaInstances.get(peerName).stop();

                return LOGGER
                        .exit(Response.status(200).type(MediaType.TEXT_PLAIN)
                                .entity(ERR_SUCCESS).build());
            } catch (Exception e) {
                LOGGER.catching(e);
                return LOGGER.exit(Response.status(500)
                        .type(MediaType.TEXT_PLAIN).entity(ERR_SERVER).build());
            }
        }
    }

    /**
     * POST /peer: Create a new peer on this service. Consumes application/json
     * (PeerConfigurationInfo).
     * 
     * @param adminKey
     *            admin key
     * @param pci
     *            PeerConfigurationInfo
     * @return (application/json) PeerInfo
     */
    @POST()
    @Path("/peer")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response updatePeerConfiguration(
            @QueryParam("adminKey") final String adminKey,
            final PeerConfigurationInfo pci) {

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;
        EntityTransaction et = null;
        String peerName = pci.getName();

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();

            boolean needPersist = false;
            PeerConfiguration pc = PeerConfiguration.findRaw(peerName);
            if (pc == null) {
                pc = new PeerConfiguration();
                needPersist = true;
            }

            pc.setGroupMgmtURL(pci.getGroupMgmtURL());
            pc.setName(pci.getName());
            pc.setRegistrationCode(pci.getRegistrationCode());
            pc.setPrivateKey(pci.getPrivateKey());
            pc.setPublicKey(pci.getPublicKey());
            pc.setPeerType(pci.getPeerType());
            pc.setFinalResultsURL(pci.getFinalResultsURL());

            if (needPersist) {
                em.persist(pc);
            }
            et.commit();

            return LOGGER.exit(register(pci.getName(),
                    pci.getRegistrationCode(), pci.getPeerType()));

        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).type(MediaType.TEXT_PLAIN)
                    .entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * Verify the admin key
     * 
     * @param adminKey
     *            admin key
     * @return true or false
     */
    public synchronized boolean verifyAdminKey(final String adminKey) {
        return this.adminKey.equals(adminKey);
    }

    /**
     * Verify the registration code of a peer. Uses a cache.
     * 
     * @param peerName
     *            Name of the peer
     * @param registrationCode
     *            registration code to verify
     * @return true or false
     */
    public synchronized boolean verifyRegistrationCode(final String peerName,
            final String registrationCode) {
        if (registrationCodes.get(peerName) == null) {

            PeerConfigurationInfo pci = PeerConfiguration.find(peerName);
            String regCode = pci.getRegistrationCode();

            registrationCodes.put(peerName, regCode);
        }

        String regCode = registrationCodes.get(peerName);
        return regCode.equals(registrationCode);
    }
}
