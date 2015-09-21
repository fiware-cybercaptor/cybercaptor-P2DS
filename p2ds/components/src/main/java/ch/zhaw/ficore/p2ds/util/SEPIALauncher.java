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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import javax.naming.NamingException;
import javax.xml.bind.JAXBException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.connections.InputPeerConnectionManager;
import ch.ethz.sepia.connections.PrivacyPeerConnectionManager;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;
import ch.ethz.sepia.startup.PeerStarter;
import ch.zhaw.ficore.p2ds.group.json.GroupConfigurationInfo;
import ch.zhaw.ficore.p2ds.group.json.GroupInfo;
import ch.zhaw.ficore.p2ds.group.json.PeerInfo;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;

public class SEPIALauncher implements Observer {
    private final static Map<String, String> inputPeerProtocolMappings = new HashMap<String, String>();

    private final static Map<String, String> privacyPeerProtocolMappings = new HashMap<String, String>();

    static {
        /*
         * ch.ethz.sepia.mpc.additive.AdditivePeer
         * ch.ethz.sepia.mpc.additive.AdditivePrivacyPeer
         */
        inputPeerProtocolMappings.put("additive",
                "ch.ethz.sepia.mpc.additive.AdditivePeer");
        privacyPeerProtocolMappings.put("additive",
                "ch.ethz.sepia.mpc.additive.AdditivePrivacyPeer");
    }
    private final Configuration cfg;

    Stopper globalStopper = new Stopper();
    private final String groupMgmtUrl;
    /** The logger. */
    private final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(SEPIALauncher.class));
    Stopper mpcStopper = new Stopper();
    private final PeerInfo peerInfo;

    private final String peerName;
    private final String regCode;
    private final Map<String, SEPIALauncher> sepiaInstances;
    Stopper stopper = new Stopper();

    public SEPIALauncher(final PeerInfo pi, final GroupInfo gi,
            final GroupConfigurationInfo rc,
            final Map<String, SEPIALauncher> sepiaInstances,
            final String regCode, final String groupMgmtUrl,
            final PrivateKey privateKey, final String finalResultsURL)
            throws NamingException, UnsupportedEncodingException,
            ClientHandlerException, UniformInterfaceException, JAXBException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        this.LOGGER.entry(pi, gi);

        this.LOGGER.info("Launching for " + pi.getPeerName());

        this.peerInfo = pi;
        this.regCode = regCode;

        this.groupMgmtUrl = groupMgmtUrl;

        this.peerName = pi.getPeerName();

        this.sepiaInstances = sepiaInstances;

        boolean isInputPeer = false;
        String peerName = pi.getPeerName();

        if (pi.getPeerType() == PeerInfo.PEER_TYPE_INPUT) {
            isInputPeer = true;
        }

        this.cfg = Configuration.getInstance(peerName);
        this.cfg.setPrivateKey(privateKey);
        this.cfg.setInputDataReader(new RESTInputDataReader());
        RESTFinalResultsWriter fsw = new RESTFinalResultsWriter(
                finalResultsURL, this.peerName);
        fsw.setMaxSize(rc.getResultBufferSize());
        this.cfg.setFinalResultsWriter(fsw);

        if (pi.getPeerType() == PeerInfo.PEER_TYPE_INPUT) {
            this.cfg.setConnectionManager(new InputPeerConnectionManager(pi
                    .getPeerName()));
        } else if (pi.getPeerType() == PeerInfo.PEER_TYPE_PRIVACY) {
            this.cfg.setConnectionManager(new PrivacyPeerConnectionManager(pi
                    .getPeerName()));
        }

        ConnectionManager cm = this.cfg.getConnectionManager();

        Properties props = this.cfg.getProperties();
        if (props == null) {
            props = new Properties();
            this.cfg.setProperties(props);
        }

        props.put(Configuration.PROP_MY_PEER_ID, peerName);
        props.put(Configuration.PROP_SYNCHRONIZE_SHARES, "true");
        props.put(Configuration.PROP_SKIP_INPUT_VERIFICATION, "false");

        String activePrivacyPeers = "";
        String activeInputPeers = "";

        int numInputPeers = 0;
        int numPrivacyPeers = 0;

        for (PeerInfo piGroup : gi.getPeers()) {

            this.LOGGER.info(" looking at " + piGroup.getPeerName());

            this.cfg.addPeerInfo(piGroup.getPeerName(), piGroup);

            byte[] pubKeyData = Certificates.decodeBase64(piGroup
                    .getPublicKey());
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyData);
            Provider provider = new BouncyCastleProvider();
            KeyFactory keyFactory = KeyFactory.getInstance("EC", provider);
            PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);
            this.cfg.setPublicKey(piGroup.getPeerName(), pubKey);

            if (piGroup.getPeerName().equals(pi.getPeerName())) {
                // continue;
            }

            if (piGroup.getPeerType() == PeerInfo.PEER_TYPE_PRIVACY) {
                activePrivacyPeers += piGroup.getPeerName() + " "
                        + piGroup.getUrl() + ";";
                numPrivacyPeers++;
            } else {
                activeInputPeers += piGroup.getPeerName() + " "
                        + piGroup.getUrl() + ";";
                numInputPeers++;
            }

            cm.createQueues(piGroup.getPeerName(), new SEPIAOutputStrategy(
                    piGroup.getPeerName(), peerName));

        }

        if (activePrivacyPeers.length() > 0) {
            activePrivacyPeers = activePrivacyPeers.substring(0,
                    activePrivacyPeers.length() - 1); // remove
        }

        if (activeInputPeers.length() > 0) {
            activeInputPeers = activeInputPeers.substring(0,
                    activeInputPeers.length() - 1);
        }
        // last
        // semicolon

        this.LOGGER.info("activePrivacyPeers :=" + activePrivacyPeers);
        this.LOGGER.info("activeInputPeers := " + activeInputPeers);

        props.put(Configuration.PROP_ACTIVE_PRIVACY_PEERS, activePrivacyPeers);
        props.put(Configuration.PROP_ACTIVE_INPUT_PEERS, activeInputPeers);

        props.put(Configuration.PROP_MPC_CUSTOM_PEER_CLASS,
                inputPeerProtocolMappings.get(rc.getMpcProtocol()));
        props.put(Configuration.PROP_MPC_CUSTOM_PRIVACYPEER_CLASS,
                privacyPeerProtocolMappings.get(rc.getMpcProtocol()));

        props.put(Configuration.PROP_FIELD, rc.getField());
        props.put(Configuration.PROP_MAXELEMENT, rc.getMaxElement());
        props.put(Configuration.PROP_NUMBER_OF_TIME_SLOTS,
                Integer.toString(rc.getNumberOfTimeSlots()));
        props.put(Configuration.PROP_NUMBER_OF_ITEMS,
                Integer.toString(rc.getNumberOfItems()));

        props.put(Configuration.PROP_MIN_PRIVACYPEERS,
                Integer.toString(numPrivacyPeers));
        props.put(Configuration.PROP_MIN_INPUTPEERS,
                Integer.toString(numInputPeers));

        Stopper stopListener = new Stopper();
        stopListener.addObserver(this);

        this.cfg.setStopListener(stopListener);
        this.cfg.setGlobalStopper(this.globalStopper);

        PeerStarter ps = new PeerStarter(peerName, this.stopper, isInputPeer,
                this.mpcStopper);

        Thread t = new Thread(ps);
        t.start();

        String statusUrl = this.groupMgmtUrl + "/status/"
                + URLEncoder.encode(this.peerInfo.getPeerName(), "UTF-8")
                + "?registrationCode=" + regCode + "&status=";
        RESTHelper.postRequest(statusUrl + PeerInfo.PEER_STATUS_STARTED);
    }

    public void addInputData(final String csvData) throws InterruptedException {
        ((RESTInputDataReader) this.cfg.getInputDataReader()).write(csvData);
    }

    public Stopper getStopper() {
        return this.stopper;
    }

    public String status() {
        return "HasStopped="
                + Configuration.getInstance(this.peerName).getStopListener()
                        .isStopped()
                + "PeerName="
                + this.peerName
                + ",HasException="
                + Configuration.getInstance(this.peerName).getStopListener()
                        .hasException();
    }

    public void stop() {
        this.mpcStopper.stop();
        this.stopper.stop();
        this.globalStopper.stop();
        this.LOGGER.info("SEPIA has stopped!");
        synchronized (this.sepiaInstances) {
            this.sepiaInstances.remove(this.peerName);
        }

        try {
            String statusUrl = this.groupMgmtUrl + "/status/"
                    + URLEncoder.encode(this.peerInfo.getPeerName(), "UTF-8")
                    + "?registrationCode=" + this.regCode + "&status=";
            RESTHelper.postRequest(statusUrl + PeerInfo.PEER_STATUS_STOPPED);
        } catch (ClientHandlerException | UniformInterfaceException
                | JAXBException | NamingException e) {
            this.LOGGER.catching(e);
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            this.LOGGER.catching(e);
        }
    }

    @Override
    public void update(final Observable o, final Object arg) {
        this.LOGGER.info("update");

        String statusUrl = null;
        try {
            statusUrl = this.groupMgmtUrl + "/status/"
                    + URLEncoder.encode(this.peerInfo.getPeerName(), "UTF-8")
                    + "?registrationCode=" + this.regCode + "&status=";
        } catch (UnsupportedEncodingException e1) {
            this.LOGGER.catching(e1);
        }
        this.LOGGER.info(statusUrl);

        Stopper stopper = (Stopper) o;
        if (stopper.isStopped() && !stopper.hasException()) {
            this.LOGGER.info("SEPIA has stopped!");
            synchronized (this.sepiaInstances) {
                this.sepiaInstances.remove(this.peerName);
            }

            try {
                RESTHelper
                        .postRequest(statusUrl + PeerInfo.PEER_STATUS_STOPPED);
            } catch (ClientHandlerException | UniformInterfaceException
                    | JAXBException | NamingException e) {
                this.LOGGER.catching(e);
            }
        } else if (stopper.isStopped() && stopper.hasException()) {
            this.LOGGER.info("SEPIA has stopped WITH exceptions!");
            if (stopper.getException() != null) {
                this.LOGGER.catching(stopper.getException());
            }
            try {
                RESTHelper.postRequest(statusUrl + PeerInfo.PEER_STATUS_ERROR);
            } catch (ClientHandlerException | UniformInterfaceException
                    | JAXBException | NamingException e) {
                this.LOGGER.catching(e);
            }
        }
    }
}
