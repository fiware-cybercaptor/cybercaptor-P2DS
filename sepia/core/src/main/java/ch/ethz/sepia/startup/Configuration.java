// Copyright 2010-2012 Martin Burkhart (martibur@ethz.ch)
//
// This file is part of SEPIA. SEPIA is free software: you can redistribute
// it and/or modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation, either version 3
// of the License, or (at your option) any later version.
//
// SEPIA is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.

package ch.ethz.sepia.startup;

import java.io.FileInputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.connections.FinalResultsWriter;
import ch.ethz.sepia.connections.InputDataReader;
import ch.ethz.sepia.mpc.PeerBase;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * This class stores the configuration file properties in a central place. Keys
 * and default values for the basic properties (peers.*, connection.*, mpc.*)
 * can also be found here. ConfigFile is implemented as a singleton, so use
 * {@link #getInstance()} to access it.
 * 
 * @author Martin Burkhart
 */
public class Configuration {

    /** The default field size to use */
    public static final String DEFAULT_FIELD = "9223372036854775783";
    public static final String DEFAULT_INPUT_DIR = "input";
    public static final String DEFAULT_INPUT_TIMEOUT = "300";
    public static final String DEFAULT_MIN_PRIVACYPEERS = "3";
    public static final String DEFAULT_OUTPUT_DIR = "output";

    public static final String DEFAULT_PARALLEL_OPERATIONS_COUNT = "0"; // run
                                                                        // all
                                                                        // operations
                                                                        // in
                                                                        // parallel
    /** The default pseudo-random generator to use if property is missing */
    public static final String DEFAULT_PRG = PeerBase.PRG_LIST[0];
    public static final String DEFAULT_TIMEOUT = "10000";
    /** The default for using message compression */
    public static final String DEFAULT_USE_COMPRESSION = "false";
    private static Map<String, Configuration> instances = new HashMap<String, Configuration>();;
    /** The logger. */
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(Configuration.class));
    public static final String PROP_ACTIVE_INPUT_PEERS = "peers.activeinputpeers";
    /** The privacy peers that are taking part in this round */
    public static final String PROP_ACTIVE_PRIVACY_PEERS = "peers.activeprivacypeers";
    /** property if messages shall be compressed */
    public static final String PROP_CONNECTION_USE_COMPRESSION = "connection.usecompression";

    // =========================================================================
    // Properties for basic MPC settings and Privacy Peers (prefix mpc.*)
    // =========================================================================

    /** degree of polynomials (t) */
    public static final String PROP_DEGREE = "mpc.degree";
    /** Field order */
    public static final String PROP_FIELD = "mpc.field";
    /** The folder holding input files */
    public static final String PROP_INPUT_DIR = "mpc.inputdirectory";
    public static final String PROP_INPUT_TIMEOUT = "mpc.inputtimeout";
    public static String PROP_KEY_PASSWORD = "connection.keypassword";
    public static String PROP_KEY_STORE = "connection.keystore";
    public static String PROP_KEY_STORE_ALIAS = "connection.keystorealias";
    public static String PROP_KEY_STORE_PASSWORD = "connection.keystorepassword";
    /** maximum values accepted in input files */
    public static final String PROP_MAXELEMENT = "mpc.maxelement";
    // =========================================================================
    // Properties for both Input Peers and Privacy Peers (prefix peers.*)
    // =========================================================================
    /** The minimum number of input peers to participate in this round */
    public static final String PROP_MIN_INPUTPEERS = "peers.minpeers";
    /** The minimum number of privacy peers to participate in this round */
    public static final String PROP_MIN_PRIVACYPEERS = "mpc.minpeers";

    /** The class for input peers to be started in the CUSTOM framework */
    public static final String PROP_MPC_CUSTOM_PEER_CLASS = "mpc.peerclass";
    /** The class for privacy peers to be started in the CUSTOM framework */
    public static final String PROP_MPC_CUSTOM_PRIVACYPEER_CLASS = "mpc.privacypeerclass";
    /** The type of the mpc peer to use */
    public static final String PROP_MPC_PEER_TYPE = "mpc.peertype";
    /** My (unique) peer ID */
    public static final String PROP_MY_PEER_ID = "peers.mypeerid";
    public static final String PROP_NUMBER_OF_ITEMS = "peers.numberofitemsintimeslot";
    public static final String PROP_NUMBER_OF_TIME_SLOTS = "peers.numberoftimeslots";
    /** The folder for output files */
    public static final String PROP_OUTPUT_DIR = "mpc.outputdirectory";
    /** name of the parallel operations count property */
    public static final String PROP_PARALLEL_OPERATIONS_COUNT = "mpc.paralleloperationscount";
    /** The pseudo random generator to use */
    public static final String PROP_PRG = "mpc.prg";

    /** do input verification? */
    public static final String PROP_SKIP_INPUT_VERIFICATION = "mpc.skipinputverification";

    /**
     * In the multiplication operation, missing shares can lead to inconsistent
     * states. if this property is set to <code>true</code>, missing shares are
     * synchronized.
     */
    public static final String PROP_SYNCHRONIZE_SHARES = "mpc.synchronizeshares";

    // =========================================================================
    // Properties for connection setup (prefix connection.*)
    // =========================================================================
    /** The timeout in milliseconds (e.g. when waiting for connections...) */
    public static final String PROP_TIMEOUT = "connection.timeout";

    /**
     * Get an instance of the ConfigFile class.
     * 
     * @return
     */
    public synchronized static Configuration getInstance(final String peerName) {
        assert peerName != null;

        synchronized (instances) {
            if (instances.get(peerName) == null) {
                final Configuration instance = new Configuration();
                instances.put(peerName, instance);
            }

            return instances.get(peerName);
        }
    }

    public synchronized static boolean hasInstance(final String peerName) {
        synchronized (instances) {
            /*
             * for (String key : instances.keySet()) { logger.info(" instance: "
             * + key); }
             */
            return instances.get(peerName) != null;
        }
    }

    private ConnectionManager connectionManager;

    private FinalResultsWriter finalResultsWriter;

    private Stopper globalStopper;
    private InputDataReader inputDataReader;

    private PrivateKey myPrivateKey;

    /**
     * This is a bit unconventional but since sepia doesn't know the
     * PeerInfo-class and we don't want sepia to depend on p2ds we use this.
     */
    private final Map<String, Object> peerInfoMap = new HashMap<String, Object>();

    private Properties properties;

    private final Map<String, PublicKey> publicKeys = new HashMap<String, PublicKey>();
    private Stopper stopListener;

    /**
     * Private constructor (singleton pattern).
     */
    private Configuration() {
    }

    public void addPeerInfo(final String peer, final Object pi) {
        this.peerInfoMap.put(peer, pi);
    }

    public ConnectionManager getConnectionManager() {
        return this.connectionManager;
    }

    public FinalResultsWriter getFinalResultsWriter() {
        return this.finalResultsWriter;
    }

    public Stopper getGlobalStopper() {
        return this.globalStopper;
    }

    public synchronized String getInput(final Integer timeSlot) {
        return null;
    }

    public InputDataReader getInputDataReader() {
        return this.inputDataReader;
    }

    public Object getPeerInfo(final String peer) {
        return this.peerInfoMap.get(peer);
    }

    public PrivateKey getPrivateKey() {
        return this.myPrivateKey;
    }

    /**
     * Gets the properties.
     * 
     * @return the properties
     */
    public Properties getProperties() {
        return this.properties;
    }

    public PublicKey getPublicKey(final String remotePeer) {
        return this.publicKeys.get(remotePeer);
    }

    public Stopper getStopListener() {
        return this.stopListener;
    }

    /**
     * Loads the properties from a file. If the file cannot be opened, an empty
     * set of properties is created.
     * 
     * @param configFileName
     *            the name of the configuration file
     */
    public void initialize(final String configFileName) {
        this.properties = new Properties();

        if (configFileName != null) {
            try {
                final FileInputStream in = new FileInputStream(configFileName);
                this.properties.load(in);
                in.close();
            } catch (final Exception e) {
                System.err.println("Error when loading properties from: "
                        + configFileName + "(" + Utils.getStackTrace(e)
                        + ") -> setting defaults");
            }
        }
    }

    public void setConnectionManager(final ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void setFinalResultsWriter(
            final FinalResultsWriter finalResultsWriter) {
        this.finalResultsWriter = finalResultsWriter;
    }

    public void setGlobalStopper(final Stopper globalStopper) {
        this.globalStopper = globalStopper;
    }

    public void setInputDataReader(final InputDataReader inputDataReader) {
        this.inputDataReader = inputDataReader;
    }

    public void setPrivateKey(final PrivateKey newPrivateKey) {
        this.myPrivateKey = newPrivateKey;
    }

    /**
     * Sets the properties.
     * 
     * @param properties
     *            the properties
     */
    public void setProperties(final Properties properties) {
        this.properties = properties;
    }

    public void setPublicKey(final String remotePeer, final PublicKey pubKey) {
        this.publicKeys.put(remotePeer, pubKey);
    }

    public synchronized void setResult(final Integer timeSlot,
            final String result) {
        logger.info("SetResult " + timeSlot + " -> " + result);
    }

    public void setStopListener(final Stopper stopListener) {
        this.stopListener = stopListener;
    }
}
