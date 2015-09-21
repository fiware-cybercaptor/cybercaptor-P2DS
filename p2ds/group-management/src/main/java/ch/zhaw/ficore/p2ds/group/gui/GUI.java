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
package ch.zhaw.ficore.p2ds.group.gui;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import ch.zhaw.ficore.p2ds.group.GroupMgmtService;
import ch.zhaw.ficore.p2ds.group.json.GroupConfigurationInfo;
import ch.zhaw.ficore.p2ds.group.storage.Group;
import ch.zhaw.ficore.p2ds.group.storage.GroupConfiguration;
import ch.zhaw.ficore.p2ds.group.storage.Manager;
import ch.zhaw.ficore.p2ds.group.storage.Peer;
import ch.zhaw.ficore.p2ds.group.storage.Registration;

import com.hp.gagawa.java.elements.A;
import com.hp.gagawa.java.elements.B;
import com.hp.gagawa.java.elements.Body;
import com.hp.gagawa.java.elements.Br;
import com.hp.gagawa.java.elements.Div;
import com.hp.gagawa.java.elements.Form;
import com.hp.gagawa.java.elements.H1;
import com.hp.gagawa.java.elements.H2;
import com.hp.gagawa.java.elements.H3;
import com.hp.gagawa.java.elements.H4;
import com.hp.gagawa.java.elements.H5;
import com.hp.gagawa.java.elements.Head;
import com.hp.gagawa.java.elements.Html;
import com.hp.gagawa.java.elements.Input;
import com.hp.gagawa.java.elements.Label;
import com.hp.gagawa.java.elements.Li;
import com.hp.gagawa.java.elements.Link;
import com.hp.gagawa.java.elements.P;
import com.hp.gagawa.java.elements.Pre;
import com.hp.gagawa.java.elements.Script;
import com.hp.gagawa.java.elements.Table;
import com.hp.gagawa.java.elements.Td;
import com.hp.gagawa.java.elements.Text;
import com.hp.gagawa.java.elements.Title;
import com.hp.gagawa.java.elements.Tr;
import com.hp.gagawa.java.elements.Ul;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;

@Path("/")
public class GUI {

    private static char[] chars = null;
    private static String cssURL = "/res/style.css";
    private static String jsURL = "/res/js.js";
    private static final int MAX_TOKENS = 2048;
    private static Random rand = new Random();

    public static String genCSRFToken(final HttpServletRequest req) {

        if (chars == null) {
            chars = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
        }

        HttpSession session = req.getSession(true);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars[Math.abs(rand.nextInt()) % chars.length]);
        }
        String token = sb.toString();

        session.setAttribute("token", token);

        return token;

    }

    public static Body getBody(final Div mainDiv) {
        mainDiv.setCSSClass("mainDiv");
        Div containerDiv = new Div().setCSSClass("containerDiv");
        mainDiv.appendChild(new H1().appendChild(new Text("Group Management")));
        Div navDiv = new Div().setCSSClass("navDiv");
        containerDiv.appendChild(navDiv);
        containerDiv.appendChild(mainDiv);

        navDiv.appendChild(new Div().setStyle("clear: both"));
        return new Body().appendChild(containerDiv);
    }

    public static Html getHtml(final String title, final HttpServletRequest req) {
        Html html = new Html();
        Head head = new Head().appendChild(new Title().appendChild(new Text(
                title)));
        html.appendChild(head);
        head.appendChild(new Link().setHref(req.getContextPath() + cssURL)
                .setRel("stylesheet").setType("text/css"));
        head.appendChild(new Script("text/javascript").setSrc(req
                .getContextPath() + jsURL));
        return html;
    }

    public static boolean isCSRFTokenValid(final String token,
            final HttpServletRequest req) {
        HttpSession session = req.getSession(true);

        Object o = session.getAttribute("token");
        if (o == null) {
            return false;
        }
        return ((String) o).equals(token);
    }

    public static Html simplePage(final String title, final String message,
            final HttpServletRequest req) {
        Html html = getHtml(title, req);
        Div mainDiv = new Div();
        Body body = getBody(mainDiv);
        html.appendChild(body);
        P p = new P().appendChild(new Text(message));
        mainDiv.appendChild(p);
        A a = new A().setHref("./").appendChild(new Text("Go to overview!"));
        mainDiv.appendChild(a);
        return html;
    }

    @Context
    ServletContext context;

    @Context
    HttpServletRequest request;

    @Path("/createGroup")
    @POST
    public Response createGroup(@FormParam("name") final String name,
            @FormParam("token") final String token) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
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
            return Response.ok(
                    simplePage("Create registration",
                            "Group has been created!", this.request).write())
                    .build();
        } catch (Exception e) {

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return Response.ok(
                    simplePage("Create registration",
                            "An error has occured: " + e.getMessage(),
                            this.request).write()).build();
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @Path("/createRegistration")
    @POST
    public Response createRegistration(@FormParam("gid") final long gid,
            @FormParam("token") final String token) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            if (!Group.exists(gid)) {
                return Response.ok(
                        simplePage("Create registration",
                                "An error has occured: Group does not exist!",
                                this.request).write()).build();
            }

            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();
            Registration r = new Registration(gid);
            em.persist(r);
            et.commit();
            return Response.ok(
                    simplePage(
                            "Create registration",
                            "Success! Registration code: "
                                    + r.getRegistrationCode(), this.request)
                            .write()).build();
        } catch (Exception e) {

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return Response.ok(
                    simplePage("Create registration",
                            "An error has occured: " + e.getMessage(),
                            this.request).write()).build();
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    public Response csrfError() {
        return Response.ok(
                simplePage("CSRF PROTECTION ERROR", "Invalid CSRF token!",
                        this.request).write()).build();
    }

    public Input csrfHidden(final String token) {
        return new Input().setType("hidden").setName("token").setValue(token);
    }

    @Path("/deleteGroup")
    @POST
    public Response deleteGroup(@FormParam("gid") final long gid,
            @FormParam("token") final String token) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        EntityManager em = Manager.getEntityManager();
        EntityTransaction et = em.getTransaction();
        et.begin();

        // FIXME: Check if peers are started? Or just ignore that and delete
        // anyway?
        try {
            Group g = em.find(Group.class, gid);
            if (g == null) {
                return Response.ok(
                        simplePage("Delete group",
                                "An error has occured: Group does not exist!",
                                this.request).write()).build();
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

            return Response.ok(
                    simplePage("Delete group", "Group has been deleted!",
                            this.request).write()).build();
        } catch (Exception e) {
            if (et != null && et.isActive()) {
                et.rollback();
            }

            return Response.ok(
                    simplePage("Delete group",
                            "An error has occured: " + e.getMessage(),
                            this.request).write()).build();
        } finally {
            em.close();
        }
    }

    @Path("/deletePeer")
    @POST
    public Response deletePeer(@FormParam("pid") final long pid,
            @FormParam("token") final String token) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();

            et.begin();

            Peer p = em.find(Peer.class, pid);
            if (p == null) {
                return Response.ok(
                        simplePage("Delete peer",
                                "An error has occured: Peer does not exist!",
                                this.request).write()).build();
            }

            em.remove(p);
            et.commit();
            return Response.ok(
                    simplePage("Delete peer", "Peer has been deleted!",
                            this.request).write()).build();
        } catch (Exception ex) {
            if (et != null && et.isActive()) {
                et.rollback();
            }
            return Response.ok(
                    simplePage("Delete group",
                            "An error has occured: " + ex.getMessage(),
                            this.request).write()).build();
        } finally {
            em.close();
        }
    }

    @Path("/deleteRegistration")
    @POST
    public Response deleteRegistration(
            @FormParam("registrationCode") final String registrationCode,
            @FormParam("token") final String token) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {

            em = Manager.getEntityManager();
            et = em.getTransaction();
            et.begin();
            Registration r = em.find(Registration.class, registrationCode);
            em.remove(r);
            et.commit();
            return Response
                    .ok(simplePage("Delete registration",
                            "Registration code has been deleted!", this.request)
                            .write()).build();
        } catch (Exception e) {

            if (et != null && et.isActive()) {
                et.rollback();
            }

            return Response.ok(
                    simplePage("Delete registration",
                            "An error has occured: " + e.getMessage(),
                            this.request).write()).build();
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @Path("/setConfiguration")
    @POST
    public Response setConfiguration(@FormParam("gid") final long gid,
            @FormParam("maxelement") final String maxelement,
            @FormParam("field") final String field,
            @FormParam("mpcprotocol") final String mpcprotocol,
            @FormParam("numberofitems") final int numberofitems,
            @FormParam("numberoftimeslots") final int numberoftimeslots,
            @FormParam("token") final String token,
            @FormParam("resultbuffersize") final int resultbuffersize) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        GroupConfigurationInfo gci = new GroupConfigurationInfo();

        gci.setField(field);
        gci.setMaxElement(maxelement);
        gci.setNumberOfItems(numberofitems);
        gci.setNumberOfTimeSlots(numberoftimeslots);
        gci.setMpcProtocol(mpcprotocol);
        gci.setGid(gid);
        gci.setResultBufferSize(resultbuffersize);

        GroupMgmtService.setConfigurationFromInfo(gci, gid);
        Html html = simplePage("Set configuration",
                "Configuration has been set!", this.request);
        return Response.ok(html.write()).build();
    }

    @Path("/startPeers")
    @POST
    public Response startPeers(@FormParam("gid") final long gid,
            @FormParam("token") final String token)
            throws UnsupportedEncodingException, ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        GroupMgmtService.startPeers(gid);
        Html html = simplePage("Start peers", "Peers have been started!",
                this.request);
        return Response.ok(html.write()).build();
    }

    @Path("")
    @GET()
    public Response status() {

        String token = genCSRFToken(this.request);

        Html html = getHtml("Status", this.request);
        Div mainDiv = new Div();
        html.appendChild(getBody(mainDiv));

        EntityManager em = Manager.getEntityManager();

        TypedQuery<Group> query = em.createQuery("SELECT g FROM TBL_GROUP g",
                Group.class);
        List<Group> groups = query.getResultList();

        mainDiv.appendChild(new H2().appendChild(new Text("Groups")));

        P p = new P()
                .appendChild(new Text(
                        "Click on the name of a group (headings) to expand and show more information!"));
        mainDiv.appendChild(p);

        mainDiv.appendChild(new B().appendChild(new Text("Group creation:")));
        mainDiv.appendChild(new Br());
        Form createForm = new Form("./createGroup").setMethod("post");
        createForm.appendChild(new Label().appendChild(new Text("Name: ")));
        createForm.appendChild(new Input().setType("text").setName("name"));
        createForm.appendChild(new Input().setType("submit").setValue(
                "Create group!"));
        createForm.appendChild(csrfHidden(token));
        mainDiv.appendChild(createForm);

        for (Group group : groups) {
            Div groupTopDiv = new Div().setCSSClass("groupDiv").setId(
                    "gid" + group.getGid());
            Div groupDiv = new Div().setCSSClass("groupDivSub").setId(
                    "gids" + group.getGid());

            mainDiv.appendChild(groupTopDiv);
            H3 h3 = new H3().appendChild(new Text(group.getName()));
            h3.setAttribute("onclick", "toggle('gids" + group.getGid() + "');");
            h3.setCSSClass("toggle");
            groupTopDiv.appendChild(h3);
            groupTopDiv.appendChild(groupDiv);
            TypedQuery<Long> members = em.createQuery(
                    "SELECT c.pid FROM TBL_PEER c WHERE c.gid ="
                            + group.getGid(), Long.class);

            List<Integer> stati = new ArrayList<Integer>();

            H4 h4 = new H4().appendChild(new Text("Actions"));
            h4.setAttribute("onclick", "toggle('act" + group.getGid() + "');");
            h4.setCSSClass("toggle");

            groupDiv.appendChild(h4);
            Div actDiv = new Div().setCSSClass("configDiv").setId(
                    "act" + group.getGid());
            Form actForm = new Form("./startPeers").setMethod("post");
            actDiv.appendChild(actForm);
            actForm.appendChild(new Input().setType("submit").setValue(
                    "Start peers"));
            actForm.appendChild(new Input().setType("hidden")
                    .setValue("" + group.getGid()).setName("gid"));
            actForm.appendChild(csrfHidden(token));
            actForm = new Form("./stopPeers").setMethod("post");
            actDiv.appendChild(actForm);
            actForm.appendChild(new Input().setType("hidden")
                    .setValue("" + group.getGid()).setName("gid"));
            actForm.appendChild(new Input().setType("submit").setValue(
                    "Stop peers"));
            actForm.appendChild(csrfHidden(token));
            groupDiv.appendChild(actDiv);

            actForm = new Form("./deleteGroup").setMethod("post");
            actDiv.appendChild(actForm);
            actForm.appendChild(new Input().setType("hidden")
                    .setValue("" + group.getGid()).setName("gid"));
            actForm.appendChild(new Input().setType("submit").setValue(
                    "Delete group"));
            actForm.appendChild(csrfHidden(token));
            groupDiv.appendChild(actDiv);

            actForm = new Form("./createRegistration").setMethod("post");
            actDiv.appendChild(actForm);
            actForm.appendChild(new Input().setType("hidden")
                    .setValue("" + group.getGid()).setName("gid"));
            actForm.appendChild(new Input().setType("submit").setValue(
                    "Create registration"));
            actForm.appendChild(csrfHidden(token));
            groupDiv.appendChild(actDiv);

            h4 = new H4().appendChild(new Text("Configuration"));
            h4.setAttribute("onclick", "toggle('cfg" + group.getGid() + "');");
            h4.setCSSClass("toggle");

            groupDiv.appendChild(h4);

            Div form = new Div().setCSSClass("configDiv").setId(
                    "cfg" + group.getGid());
            Form f = new Form("./setConfiguration").setMethod("post");
            form.appendChild(f);
            f.appendChild(csrfHidden(token));

            TypedQuery<GroupConfiguration> gcQuery = em.createQuery(
                    "SELECT g FROM TBL_CONFIGURATION g WHERE g.gid ="
                            + group.getGid(), GroupConfiguration.class);

            GroupConfiguration gc = null;
            List<GroupConfiguration> gcs = gcQuery.getResultList();
            if (gcs.size() > 0) {
                gc = gcs.get(0);
            } else {
                gc = new GroupConfiguration();
            }

            Table table = new Table();
            Tr tr = new Tr();
            f.appendChild(table);

            tr.appendChild(new Td().appendChild(new Label()
                    .appendChild(new Text("Field:"))));
            tr.appendChild(new Td().appendChild(new Input().setName("field")
                    .setValue(gc.getField())));
            table.appendChild(tr);

            tr = new Tr();
            tr.appendChild(new Td().appendChild(new Label()
                    .appendChild(new Text("MaxElement:"))));
            tr.appendChild(new Td().appendChild(new Input().setName(
                    "maxelement").setValue(gc.getMaxElement())));
            table.appendChild(tr);

            tr = new Tr();
            tr.appendChild(new Td().appendChild(new Label()
                    .appendChild(new Text("MpcProtocol:"))));
            tr.appendChild(new Td().appendChild(new Input().setName(
                    "mpcprotocol").setValue(gc.getMpcProtocol())));
            table.appendChild(tr);

            tr = new Tr();
            tr.appendChild(new Td().appendChild(new Label()
                    .appendChild(new Text("NumberOfItems:"))));
            tr.appendChild(new Td().appendChild(new Input().setName(
                    "numberofitems").setValue("" + gc.getNumberOfItems())));
            table.appendChild(tr);

            tr = new Tr();
            tr.appendChild(new Td().appendChild(new Label()
                    .appendChild(new Text("NumberOfTimeSlots:"))));
            tr.appendChild(new Td().appendChild(new Input().setName(
                    "numberoftimeslots").setValue(
                    "" + gc.getNumberOfTimeSlots())));
            table.appendChild(tr);

            tr = new Tr();
            tr.appendChild(new Td().appendChild(new Label()
                    .appendChild(new Text("ResultBufferSize:"))));
            tr.appendChild(new Td().appendChild(new Input().setName(
                    "resultbuffersize").setValue("" + gc.getResultBufferSize())));
            table.appendChild(tr);

            f.appendChild(new Input().setType("hidden").setName("gid")
                    .setValue("" + group.getGid()));

            f.appendChild(new Input().setType("submit").setValue(
                    "Set Configuration"));

            groupDiv.appendChild(form);

            h4 = new H4().appendChild(new Text("Members"));
            h4.setAttribute("onclick", "toggle('members" + group.getGid()
                    + "');");
            h4.setCSSClass("toggle");

            groupDiv.appendChild(h4);

            Div memberDiv = new Div().setId("members" + group.getGid())
                    .setCSSClass("memberDiv");
            groupDiv.appendChild(memberDiv);

            for (Long memberId : members.getResultList()) {
                Div peerDiv = new Div().setCSSClass("peerDiv").setId(
                        "pid" + memberId);
                memberDiv.appendChild(peerDiv);
                Peer peer = em.find(Peer.class, memberId);
                peerDiv.appendChild(new H5().appendChild(new Text(peer
                        .getPeerName())));
                Ul ul = new Ul();
                ul.appendChild(new Li().appendChild(new Text("Last status: "
                        + peer.getLastStatus())));
                ul.appendChild(new Li().appendChild(new Text("PeerType: "
                        + peer.getPeerType())));
                ul.appendChild(new Li().appendChild(new Text("Pid: "
                        + peer.getPid())));
                ul.appendChild(new Li().appendChild(new Text("URL: "
                        + peer.getUrl())));
                ul.appendChild(new Li().appendChild(new Text(
                        "RegistrationCode: " + peer.getRegistrationCode())));
                ul.appendChild(new Li().appendChild(new Text("Verified: "
                        + peer.isVerified())));
                Pre pre = new Pre().appendChild(new Text(peer.getPublicKey()));
                peerDiv.appendChild(ul);
                peerDiv.appendChild(new B().appendChild(new Text("PublicKey")));
                peerDiv.appendChild(new Br());
                peerDiv.appendChild(pre);
                stati.add(peer.getLastStatus());
                Form fdel = new Form("./deletePeer").setMethod("post");
                fdel.appendChild(new Input().setType("hidden").setName("pid")
                        .setValue(memberId.toString()));
                fdel.appendChild(new Input().setType("submit").setValue(
                        "Delete this peer"));
                fdel.appendChild(csrfHidden(token));
                peerDiv.appendChild(fdel);

                String value = "true";
                String text = "Mark as verified";

                if (peer.isVerified()) {
                    value = "false";
                    text = "Mark as unverified";
                }

                fdel = new Form("./verifyPeer").setMethod("post");
                fdel.appendChild(new Input().setType("hidden").setName("pid")
                        .setValue(memberId.toString()));
                fdel.appendChild(new Input().setType("hidden")
                        .setName("verified").setValue(value));
                fdel.appendChild(new Input().setType("submit").setValue(text));
                fdel.appendChild(csrfHidden(token));
                peerDiv.appendChild(fdel);
            }

            groupDiv.appendChild(new H4().appendChild(new Text(
                    "Open registration codes")));
            Div regDiv = new Div().setCSSClass("regDiv");
            groupDiv.appendChild(regDiv);
            TypedQuery<Registration> regQuery = em.createQuery(
                    "SELECT r FROM TBL_REGISTRATION r WHERE r.gid ="
                            + group.getGid(), Registration.class);

            List<Registration> registrationCodes = regQuery.getResultList();
            for (Registration reg : registrationCodes) {
                Form freg = new Form("./deleteRegistration").setMethod("post");
                freg.appendChild(new Label().appendChild(new Text("Code: ")));
                freg.appendChild(new Label().appendChild(new Text(reg
                        .getRegistrationCode() + " ")));
                freg.appendChild(new Input().setType("hidden")
                        .setName("registrationCode")
                        .setValue(reg.getRegistrationCode()));
                freg.appendChild(new Input().setType("submit").setValue(
                        "delete"));
                freg.appendChild(csrfHidden(token));
                regDiv.appendChild(freg);
            }

            String status = "n/a";
            boolean allZero = true;
            boolean allStarted = true;
            boolean allStopped = true;
            boolean hadException = false;

            for (Integer i : stati) {
                if (i != 0) {
                    allZero = false;
                }
                if (i != 1) {
                    allStarted = false;
                }
                if (i != 2 && i != 3) {
                    allStopped = false;
                }
                if (i == 2) {
                    hadException = true;
                }
            }

            if (allZero) {
                status = "Unknown. No peer has ever been started.";
            }
            if (allStarted) {
                status = "Running. All peers are running.";
            }
            if (allStopped && hadException) {
                status = "Stopped with error. All peers stopped but last run was not successful.";
            }
            if (allStopped && !hadException) {
                status = "Stopped. All peers stopped.";
            }

            groupDiv.appendChild(new H4().appendChild(new Text("Status")));

            Div statusDiv = new Div().setCSSClass("statusDiv").setId(
                    "st" + group.getGid());
            statusDiv.appendChild(new Text("Group status: " + status));

            groupDiv.appendChild(statusDiv);

        }

        return Response.ok(html.write()).build();
    }

    @Path("/stopPeers")
    @POST
    public Response stopPeers(@FormParam("gid") final long gid,
            @FormParam("token") final String token)
            throws UnsupportedEncodingException, ClientHandlerException,
            UniformInterfaceException, JAXBException, NamingException {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        GroupMgmtService.stopPeers(gid);
        Html html = simplePage("Stop peers", "Peers have been stopped!",
                this.request);
        return Response.ok(html.write()).build();
    }

    @Path("/verifyPeer")
    @POST
    public Response verifyPeer(@FormParam("pid") final long pid,
            @FormParam("token") final String token,
            @FormParam("verified") final boolean verified) {

        if (!isCSRFTokenValid(token, this.request)) {
            return csrfError();
        }

        EntityManager em = null;
        EntityTransaction et = null;

        try {
            em = Manager.getEntityManager();
            et = em.getTransaction();

            et.begin();

            Peer p = em.find(Peer.class, pid);
            if (p == null) {
                return Response.ok(
                        simplePage("Verify peer",
                                "An error has occured: Peer does not exist!",
                                this.request).write()).build();
            }

            String text = "Peer has been marked as verified";
            if (!verified) {
                text = "Peer has been marked as unverified";
            }

            p.setVerified(verified);

            et.commit();
            return Response.ok(
                    simplePage("Verify peer", text, this.request).write())
                    .build();
        } catch (Exception ex) {
            if (et != null && et.isActive()) {
                et.rollback();
            }
            return Response.ok(
                    simplePage("Delete group",
                            "An error has occured: " + ex.getMessage(),
                            this.request).write()).build();
        } finally {
            em.close();
        }
    }
}
