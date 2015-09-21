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
package ch.zhaw.ficore.p2ds.group;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.zhaw.ficore.p2ds.group.json.DataSets;
import ch.zhaw.ficore.p2ds.group.json.GroupConfigurationInfo;
import ch.zhaw.ficore.p2ds.group.json.GroupInfo;
import ch.zhaw.ficore.p2ds.group.json.GroupName;
import ch.zhaw.ficore.p2ds.group.json.PeerInfo;
import ch.zhaw.ficore.p2ds.group.storage.Group;
import ch.zhaw.ficore.p2ds.group.storage.GroupConfiguration;
import ch.zhaw.ficore.p2ds.group.storage.Manager;
import ch.zhaw.ficore.p2ds.group.storage.Peer;
import ch.zhaw.ficore.p2ds.group.storage.Registration;
import ch.zhaw.ficore.p2ds.util.Certificates;
import ch.zhaw.ficore.p2ds.util.RESTHelper;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;

@Path("/group-mgmt")
public class GroupMgmtService {

    private static final String ERR_INV_ADMIN_KEY = "ERR_INV_ADMIN_KEY";
    private static final String ERR_INV_CERT = "ERR_INV_CERT";
    private static final String ERR_INV_REG_CODE = "ERR_INV_REG_CODE";
    private static final String ERR_NO_GROUP = "ERR_NO_GROUP";
    private static final String ERR_NO_GROUP_CONFIG = "ERR_NO_GROUP_CONFIG";
    private static final String ERR_NO_PEER = "ERR_NO_PEER";
    private static final String ERR_NOT_FOUND = "ERR_NOT_FOUND";
    private static final String ERR_SERVER = "ERR_SERVER";
    private static final String ERR_SUCCESS = "ERR_SUCCESS";

    /** The logger. */
    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(GroupMgmtService.class));

    /**
     * Helper method to set GroupConfiguration based on GroupConfigurationInfo
     * 
     * @param gci
     *            GroupConfigurationInfo
     * @param gid
     *            id of the group
     * @return true or false (true on success)
     */
    public static boolean setConfigurationFromInfo(
            final GroupConfigurationInfo gci, final long gid) {

        EntityManager em = null;
        EntityTransaction et = null;

        if (!Group.exists(gid)) {
            LOGGER.info("Group does not exist " + gid);
            throw new RuntimeException("Group does not exist");
        }

        em = Manager.getEntityManager();
        et = em.getTransaction();
        et.begin();

        TypedQuery<GroupConfiguration> getConfiguration = em.createQuery(
                "SELECT g FROM TBL_CONFIGURATION g WHERE g.gid = :gid",
                GroupConfiguration.class);

        getConfiguration.setParameter("gid", gid);

        List<GroupConfiguration> results = getConfiguration.getResultList();

        GroupConfiguration g = null;

        if (results.size() > 0) {
            g = results.get(0);
        }

        boolean needToPersist = false;

        if (g == null) {
            g = new GroupConfiguration();
            g.setGid(gid);
            needToPersist = true;
        }

        g.setMaxElement(gci.getMaxElement());
        g.setMpcProtocol(gci.getMpcProtocol());
        g.setNumberOfTimeSlots(gci.getNumberOfTimeSlots());
        g.setField(gci.getField());
        g.setNumberOfItems(gci.getNumberOfItems());
        g.setResultBufferSize(gci.getResultBufferSize());

        if (needToPersist) {
            em.persist(g);
        }

        et.commit();

        return true;
    }

    /**
     * Helper method to start all peers of a group
     * 
     * @param gid
     *            Id of the group
     * @throws UnsupportedEncodingException
     *             If something went wrong.
     * @throws ClientHandlerException
     *             If something went wrong.
     * @throws UniformInterfaceException
     *             If something else went wrong.
     * @throws JAXBException
     *             If something went seriously wrong.
     * @throws NamingException
     *             If something went wrong but not really wrong but just a
     *             little bit wrong.
     */
    public static void startPeers(final long gid)
            throws UnsupportedEncodingException, ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {
        LOGGER.info("Starting peers for group " + gid);
        EntityManager em = Manager.getEntityManager();
        TypedQuery<Long> members = em.createQuery(
                "SELECT c.pid FROM TBL_PEER c WHERE c.gid =" + gid, Long.class);

        int i = 0;

        for (Long memberId : members.getResultList()) {

            Peer peer = em.find(Peer.class, memberId);
            if (!peer.isVerified()) {
                continue;
            }

            String urlSuffix = "/start/";
            urlSuffix += URLEncoder.encode(peer.getPeerName(), "UTF-8") + "/";
            urlSuffix += "?registrationCode="
                    + URLEncoder.encode(peer.getRegistrationCode(), "UTF-8");
            try {
                RESTHelper.postRequest(peer.getUrl() + urlSuffix);
                i++;
            } catch (Exception e) {
                LOGGER.catching(e);
            }
        }
        LOGGER.info("Started " + i + " peers!");

    }

    /**
     * Helper method to stop all peers of a group.
     * 
     * @param gid
     *            Id of the group.
     * @throws UnsupportedEncodingException
     *             If something went horribly wrong.
     * @throws ClientHandlerException
     *             If something went wrong.
     * @throws UniformInterfaceException
     *             If something absolutely went wrong.
     * @throws JAXBException
     *             If your Java is messed up.
     * @throws NamingException
     *             If something went a little bit wrong.
     */
    public static void stopPeers(final long gid)
            throws UnsupportedEncodingException, ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {
        LOGGER.info("Stopping peers for group " + gid);
        EntityManager em = Manager.getEntityManager();
        TypedQuery<Long> members = em.createQuery(
                "SELECT c.pid FROM TBL_PEER c WHERE c.gid =" + gid, Long.class);

        int i = 0;

        for (Long memberId : members.getResultList()) {
            Peer peer = em.find(Peer.class, memberId);
            String urlSuffix = "/stop/";
            urlSuffix += URLEncoder.encode(peer.getPeerName(), "UTF-8");
            urlSuffix += "?registrationCode="
                    + URLEncoder.encode(peer.getRegistrationCode(), "UTF-8");
            try {
                RESTHelper.postRequest(peer.getUrl() + urlSuffix);
                i++;
            } catch (Exception e) {
                LOGGER.catching(e);
            }
        }
        LOGGER.info("Stopped " + i + " peers!");

    }

    private String adminKey = null;

    private final TypedQuery<GroupConfiguration> getConfiguration;

    private final TypedQuery<Long> getGroupMembers;

    private final TypedQuery<Peer> getPeers;

    @Context
    HttpServletRequest request;

    public GroupMgmtService() throws Exception {
        EntityManager em = Manager.getEntityManager();

        this.getConfiguration = em.createQuery(
                "SELECT g FROM TBL_CONFIGURATION g WHERE g.gid = :gid",
                GroupConfiguration.class);
        this.getPeers = em.createQuery(
                "SELECT c FROM TBL_PEER c WHERE c.gid = :gid", Peer.class);
        this.getGroupMembers = em.createQuery(
                "SELECT c.pid FROM TBL_PEER c WHERE c.gid = :gid", Long.class);

        LOGGER.info("New PeerService Instance");

        InitialContext ic = new InitialContext();

        try {
            this.adminKey = (String) ic
                    .lookup("java:/comp/env/groupMgmt/adminKey");
        } catch (Exception ex) {
            LOGGER.info("using default-admin-key. (Not good if you see this in production!)");
            this.adminKey = "default-admin-key";
        }
    }

    /**
     * POST /publicKey/{peerName}: Upload and or replace a public key. This
     * method will set the peer to unverified. Consumes text/plain. Public key
     * needs to be base64 encoded.
     * 
     * @param peerName
     *            Name of the peer
     * @param registrationCode
     *            registration code
     * @param publicKey
     *            new public key.
     * @return (application/json) PeerInfo
     */
    @POST
    @Path("/publicKey/{peerName}")
    @Consumes({ MediaType.TEXT_PLAIN })
    public Response addPublicKey(@PathParam("peerName") final String peerName,
            @QueryParam("registrationCode") final String registrationCode,
            final String publicKey) {
        LOGGER.entry(peerName, registrationCode);
        LOGGER.info("Receiving public key for " + peerName);
        LOGGER.info(publicKey);

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();

            et.begin();

            Peer p = Peer.find(em, peerName);

            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .type(MediaType.TEXT_PLAIN).build());
            }

            if (!p.getRegistrationCode().equals(registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .entity(ERR_INV_REG_CODE).type(MediaType.TEXT_PLAIN)
                        .build());
            }

            if (!Certificates.isValidPublicKey(publicKey)) {
                return LOGGER.exit(Response.status(400).entity(ERR_INV_CERT)
                        .type(MediaType.TEXT_PLAIN).build());
            }

            p.setPublicKey(publicKey);
            p.setVerified(false);
            et.commit();

            return LOGGER.exit(Response.ok(Peer.toPeerInfo(p),
                    MediaType.APPLICATION_JSON).build());

        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * POST /group: Create a new group. Consumes application/json
     * 
     * @param gn
     *            GroupName
     * @param adminKey
     *            admin key
     * @return (application/json) Group
     */
    @POST
    @Path("/group")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response createGroup(final GroupName gn,
            @QueryParam("adminKey") final String adminKey) {
        String name = gn.getName();
        LOGGER.entry(name);

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();
            Group g = new Group();
            g.setName(name);
            em.persist(g);
            et.commit();
            return LOGGER.exit(Response.ok(g, MediaType.APPLICATION_JSON)
                    .build());
        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * POST /registration/{gid}: Generate a registration code that allows a peer
     * to register at the given group.
     * 
     * @param gid
     *            Id of the group.
     * @param adminKey
     *            admin key
     * @return (application/json) Registration
     */
    @POST
    @Path("/registration/{gid}")
    public Response createRegistration(@PathParam("gid") final long gid,
            @QueryParam("adminKey") final String adminKey) {
        LOGGER.entry(gid);

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            if (!Group.exists(gid)) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_GROUP)
                        .build());
            }

            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();
            Registration r = new Registration(gid);
            em.persist(r);
            et.commit();
            return LOGGER.exit(Response.ok(r, MediaType.APPLICATION_JSON)
                    .build());
        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * DELETE /group/{groupId}: Delete a group.
     * 
     * @param gid
     *            Id of the group
     * @param adminKey
     *            admin key
     * @return (text/plain) OK or ERROR.
     */
    @DELETE
    @Path("/group/{groupId}")
    public Response deleteGroup(@PathParam("groupId") final long gid,
            @QueryParam("adminKey") final String adminKey) {
        LOGGER.entry(gid);

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = Manager.getEntityManager();
        EntityTransaction et = em.getTransaction();
        et.begin();

        try {
            Group g = em.find(Group.class, gid);
            if (g == null) {
                return LOGGER.exit(Response.status(403).entity(ERR_NO_GROUP)
                        .build());
            }

            TypedQuery<GroupConfiguration> query = em.createQuery(
                    "SELECT g FROM TBL_CONFIGURATION g WHERE g.gid =" + gid,
                    GroupConfiguration.class);
            List<GroupConfiguration> results = query.getResultList();

            GroupConfiguration gc = null;

            if (results.size() > 0) {
                gc = results.get(0);
            }

            if (gc != null) {
                em.remove(gc);
            }

            TypedQuery<Long> members = em.createQuery(
                    "SELECT c.pid FROM TBL_PEER c WHERE c.gid =" + gid,
                    Long.class);

            for (Long memberId : members.getResultList()) {
                Peer peer = em.find(Peer.class, memberId);
                if (peer != null) {
                    em.remove(peer);
                }
            }

            em.remove(g);
            et.commit();

            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception e) {
            if (et != null && et.isActive()) {
                et.rollback();
            }
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            em.close();
        }
    }

    /**
     * DELETE /peer/{peerName}: Delete a peer
     * 
     * @param peerName
     *            Name of the peer
     * @param adminKey
     *            admin key
     * @return (text/plain) OK or ERROR.
     */
    @DELETE
    @Path("/peer/{peerName}")
    public Response deletePeer(@PathParam("peerName") final String peerName,
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

            et.begin();

            Peer p = Peer.find(em, peerName);
            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .build());
            }

            em.remove(p);
            et.commit();
            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception ex) {
            if (et != null && et.isActive()) {
                et.rollback();
            }
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            em.close();
        }
    }

    /**
     * DELETE /registration/{registrationCode}: Delete a registration.
     * 
     * @param registrationCode
     *            registration code
     * @param adminKey
     *            admin key
     * @return (text/plain) OK or ERROR.
     */
    @DELETE
    @Path("/registration/{registrationCode}")
    public Response deleteRegistration(
            @PathParam("registrationCode") final String registrationCode,
            @QueryParam("adminKey") final String adminKey) {
        LOGGER.entry(registrationCode);

        EntityManager em = null;
        EntityTransaction et = null;

        try {

            if (!verifyAdminKey(adminKey)) {
                return LOGGER.exit(Response.status(403)
                        .entity(ERR_INV_ADMIN_KEY).build());
            }

            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();
            Registration r = em.find(Registration.class, registrationCode);
            em.remove(r);
            et.commit();
            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * GET /configuration/{peerName}: Used by the peers to download the group
     * configuration.
     * 
     * @param peerName
     *            Name of the peer
     * @param registrationCode
     *            Registration code
     * @return (application/json) GroupConfigurationInfo
     */
    @GET
    @Path("/configuration/{peerName}")
    public Response getConfiguration(
            @PathParam("peerName") final String peerName,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(peerName, registrationCode);

        EntityManager em = null;

        try {
            em = Manager.getEntityManager();

            Peer p = Peer.find(em, peerName);
            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .type(MediaType.TEXT_PLAIN).build());
            }

            if (!p.getRegistrationCode().equals(registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .entity(ERR_INV_REG_CODE).type(MediaType.TEXT_PLAIN)
                        .build());
            }

            long gid = p.getGid();

            this.getConfiguration.setParameter("gid", gid);
            List<GroupConfiguration> results = this.getConfiguration
                    .getResultList();

            GroupConfiguration g = null;

            if (results.size() > 0) {
                g = results.get(0);
            }

            if (g == null) {
                return LOGGER.exit(Response.status(404)
                        .entity(ERR_NO_GROUP_CONFIG).type(MediaType.TEXT_PLAIN)
                        .build());
            }

            return LOGGER.exit(Response.ok(
                    GroupConfiguration.toGroupConfigurationInfo(g),
                    MediaType.APPLICATION_JSON).build());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * GET /groupInfo/{peerName}: Used by the peers to download information
     * about a group.
     * 
     * @param peerName
     *            Name of the peer
     * @param registrationCode
     *            registration code
     * @return (application/json) GroupInfo
     */
    @GET
    @Path("/groupInfo/{peerName}")
    public Response getGroupInfo(@PathParam("peerName") final String peerName,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(peerName, registrationCode);

        EntityManager em = null;

        try {
            em = Manager.getEntityManager();

            Peer p = Peer.find(em, peerName);

            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .type(MediaType.TEXT_PLAIN).build());
            }

            if (!p.getRegistrationCode().equals(registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .entity(ERR_INV_REG_CODE).type(MediaType.TEXT_PLAIN)
                        .build());
            }

            this.getPeers.setParameter("gid", p.getGid());

            List<Peer> peers = this.getPeers.getResultList();

            GroupInfo g = new GroupInfo();
            for (Peer peer : peers) {
                g.addPeer(Peer.toPeerInfo(peer));
            }

            return LOGGER.exit(Response.ok(g, MediaType.APPLICATION_JSON)
                    .build());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * GET /peer/{peerName}: Retrieve information about a peer (incl. keys).
     * 
     * @param peerName
     *            Name of the peer
     * @param adminKey
     *            admin key
     * @return (application/json) Peer
     */
    @GET
    @Path("/peer/{peerName}")
    public Response getPeer(@PathParam("peerName") final String peerName,
            @QueryParam("adminKey") final String adminKey) {
        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;
        try {
            em = Manager.getEntityManager();

            Peer p = Peer.find(em, peerName);
            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .type(MediaType.TEXT_PLAIN).build());
            }

            return LOGGER.exit(Response.ok(p).type(MediaType.APPLICATION_JSON)
                    .build());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * GET /group/{gid}: Retrieve information about a group.
     * 
     * @param gid
     *            Id of the group
     * @param adminKey
     *            admin key
     * @return (application/json) Group
     */
    @GET
    @Path("/group/{gid}")
    public Response groupGET(@PathParam("gid") final long gid,
            @QueryParam("adminKey") final String adminKey) {
        LOGGER.entry(gid);

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;

        try {
            em = Manager.getEntityManager();
            Group g = em.find(Group.class, gid);
            if (g != null) {
                return LOGGER.exit(Response.ok(g, MediaType.APPLICATION_JSON)
                        .build());
            } else {
                return LOGGER.exit(Response.status(404).entity(ERR_NOT_FOUND)
                        .type(MediaType.TEXT_PLAIN).build());
            }
        } catch (Exception e) {
            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @POST
    @Path("/receive")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response receive(final DataSets ds) {
        for (String s : ds.getData()) {

            LOGGER.info(" data set received: " + s);
        }

        return LOGGER.exit(Response.status(200).entity(ERR_SUCCESS).build());
    }

    /**
     * POST /register/{registrationCode}: Used by the peers to register
     * themselves.
     * 
     * @param registrationCode
     *            registration code
     * @param url
     *            url of the peer
     * @param name
     *            Name of the peer
     * @param type
     *            pere type
     * @return (application/json) PeerInfo
     */
    @POST
    @Path("/register/{registrationCode}")
    public Response register(
            @PathParam("registrationCode") final String registrationCode,
            @QueryParam("url") final String url,
            @QueryParam("name") final String name,
            @QueryParam("type") final int type) {
        LOGGER.entry(registrationCode, url);

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            Registration reg = em.find(Registration.class, registrationCode);
            if (reg == null) {
                return LOGGER.exit(Response.status(400)
                        .entity(ERR_INV_REG_CODE).type(MediaType.TEXT_PLAIN)
                        .build());
            }

            et = em.getTransaction();
            et.begin();
            Peer p = new Peer();
            p.setGid(reg.getGid());
            p.setRegistrationCode(reg.getRegistrationCode());
            p.setUrl(url);
            p.setPublicKey(null);
            p.setPeerName(name);
            p.setPeerType(type);
            p.setLastStatus(PeerInfo.PEER_STATUS_UNKNOWN);
            p.setVerified(false);
            em.persist(p);
            em.remove(reg);
            et.commit();
            return LOGGER.exit(Response.ok(Peer.toPeerInfo(p),
                    MediaType.APPLICATION_JSON).build());
        } catch (Exception e) {

            if (et != null && et.isActive()) {
                et.rollback();
            }

            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    /**
     * POST /start/{gid}: Start all peers of the group.
     * 
     * @param gid
     *            Id of the group
     * @param adminKey
     *            admin key
     * @return (text/plain) OK or ERROR
     */
    @POST
    @Path("/start/{gid}")
    public Response restStartPeers(@PathParam("gid") final long gid,
            @QueryParam("adminKey") final String adminKey) {
        LOGGER.entry(gid);

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        try {
            startPeers(gid);

            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception e) {

            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        }
    }

    /**
     * POST /stop/{gid}: Stop all peers of the group.
     * 
     * @param gid
     *            Id of the group.
     * @param adminKey
     *            admin key
     * @return (text/plain) OK or ERROR
     */
    @POST
    @Path("/stop/{gid}")
    public Response restStopPeers(@PathParam("gid") final long gid,
            @QueryParam("adminKey") final String adminKey) {
        LOGGER.entry(gid);

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        try {
            stopPeers(gid);

            return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
        } catch (Exception e) {

            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        }
    }

    /**
     * POST /configuration: Set group configuration. Consumes application/json.
     * 
     * @param gci
     *            GroupConfigurationInfo
     * @param adminKey
     *            admin key
     * @return GroupConfiguration
     */
    @POST
    @Path("/configuration")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response setConfiguration(final GroupConfigurationInfo gci,
            @QueryParam("adminKey") final String adminKey) {
        long gid = gci.getGid();
        LOGGER.entry(gci, gid);

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            if (!Group.exists(gid)) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_GROUP)
                        .build());
            }

            if (!verifyAdminKey(adminKey)) {
                return LOGGER.exit(Response.status(403)
                        .entity(ERR_INV_ADMIN_KEY).build());
            }

            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();

            this.getConfiguration.setParameter("gid", gid);

            List<GroupConfiguration> results = this.getConfiguration
                    .getResultList();

            GroupConfiguration g = null;

            if (results.size() > 0) {
                g = results.get(0);
            }

            boolean needToPersist = false;

            if (g == null) {
                g = new GroupConfiguration();
                g.setGid(gid);
                needToPersist = true;
            }

            g.setMaxElement(gci.getMaxElement());
            g.setMpcProtocol(gci.getMpcProtocol());
            g.setNumberOfTimeSlots(gci.getNumberOfTimeSlots());
            g.setField(gci.getField());
            g.setNumberOfItems(gci.getNumberOfItems());

            if (needToPersist) {
                em.persist(g);
            }

            et.commit();
            return LOGGER.exit(Response.ok(g, MediaType.APPLICATION_JSON)
                    .build());

        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            em.close();
        }
    }

    /**
     * POST /status/{peerName}: Used by the peers to set their last known
     * status.
     * 
     * @param peerName
     *            name of the peer
     * @param status
     *            status code
     * @param registrationCode
     *            registration code
     * @return (text/plain) OK or ERROR
     */
    @POST
    @Path("/status/{peerName}")
    public Response setStatus(@PathParam("peerName") final String peerName,
            @QueryParam("status") final int status,
            @QueryParam("registrationCode") final String registrationCode) {
        LOGGER.entry(peerName, status, registrationCode);
        LOGGER.info(peerName + " status " + status);

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();

            Peer p = Peer.find(em, peerName);
            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .type(MediaType.TEXT_PLAIN).build());
            }

            if (!p.getRegistrationCode().equals(registrationCode)) {
                return LOGGER.exit(Response.status(400)
                        .entity(ERR_INV_REG_CODE).build());
            }

            et = em.getTransaction();
            et.begin();

            p.setLastStatus(status);

            et.commit();

            return LOGGER.exit(Response.ok(ERR_SUCCESS, MediaType.TEXT_PLAIN)
                    .build());
        } catch (Exception e) {

            if (et != null && et.isActive()) {
                et.rollback();
            }

            LOGGER.catching(e);
            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            em.close();
        }

    }

    @POST
    @Path("/testmode")
    public Response setTest(@QueryParam("adminKey") final String adminKey,
            @QueryParam("on") final boolean on) {
        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        Registration.setTest(on);
        return LOGGER.exit(Response.ok(ERR_SUCCESS).build());
    }

    /**
     * POST /verify/{peerName}: Mark peer as verified or unverified
     * 
     * @param peerName
     *            Name of the peer
     * @param adminKey
     *            admin key
     * @param verified
     *            true or false
     * @return (text/plain) OK or ERROR
     */
    @POST
    @Path("/verify/{peerName}")
    public Response setVerified(@PathParam("peerName") final String peerName,
            @QueryParam("adminKey") final String adminKey,
            @QueryParam("verified") final boolean verified) {

        if (!verifyAdminKey(adminKey)) {
            return LOGGER.exit(Response.status(403).entity(ERR_INV_ADMIN_KEY)
                    .build());
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();

            Peer p = Peer.find(em, peerName);
            if (p == null) {
                return LOGGER.exit(Response.status(404).entity(ERR_NO_PEER)
                        .type(MediaType.TEXT_PLAIN).build());
            }
            p.setVerified(verified);

            et.commit();

            return LOGGER.exit(Response.ok(p).type(MediaType.APPLICATION_JSON)
                    .build());

        } catch (Exception e) {
            LOGGER.catching(e);

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return LOGGER.exit(Response.status(500).entity(ERR_SERVER).build());
        } finally {
            if (em != null) {
                em.close();
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
     * Verifies the admin key.
     * 
     * @param adminKey
     *            admin key
     * @return true or false.
     */
    public synchronized boolean verifyAdminKey(final String adminKey) {
        return this.adminKey.equals(adminKey);
    }

}
