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

package ch.ethz.sepia.connections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.startup.Configuration;

/**
 * Base class for connection managers. All the logic associated with creation
 * and failure of connections is encapsulated in the connection manager (CM).
 * The CM provides an interface for sending and receiving messages to targets
 * identified by an ID. The sockets used to send messages are managed by the CM
 * and hidden from the input/privacy peers. In case a socket is closed by the
 * other side, it is automatically removed from the set of active connections.
 * 
 * Upon a new communication round, temporary connections must be activated (
 * {@link #activateTemporaryConnections()}). That is, all temporary connections
 * are copied to the pool of active connections and used for subsequent
 * messaging. All connections initiated later are first stored in the temporary
 * connection pool and are only made active when
 * {@link #activateTemporaryConnections()} is called again. This is to prevent
 * newly started input and privacy peers from interfering with an ongoing
 * communication round.
 * 
 * That is, the CM is used as follows:
 * 
 * <ol>
 * <li>Before a new round is started, call {@link #establishConnections()} or
 * {@link #waitForConnections(int, int, int)} to (re-)establish all possible
 * connections.</li>
 * <li>Then call {@link #activateTemporaryConnections()} to activate all
 * temporary connections for the next round.</li>
 * <li>During a round, messages are sent and received using
 * {@link #sendMessage(String, Object)} and {@link #receiveMessage(String)}. If
 * connections go down, they are automatically removed from the active
 * connection pool. In case newly started peers are connecting, they are put in
 * the temporary connection pool and are available for the next round.</li>
 * <li>Go back to step 1.</li>
 * </ol>
 * 
 * @author martibur
 */
public abstract class ConnectionManager {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(ConnectionManager.class));

    private final Map<String, BlockingQueue<Object>> inputQueues = new HashMap<String, BlockingQueue<Object>>();

    protected String myId;

    private final Map<String, OutputStrategy> outputQueues = new HashMap<String, OutputStrategy>();

    public ConnectionManager(final String myId) {
        this.myId = myId;
    }

    public void createQueues(final String otherPeerID,
            final OutputStrategy strategy) {
        logger.info("create for " + otherPeerID);
        logger.info(" OutputStrategy is " + strategy);
        this.inputQueues.put(otherPeerID, new LinkedBlockingQueue<Object>());
        this.outputQueues.put(otherPeerID, strategy);
    }

    public List<String> getActivePeers(final boolean privacyPeersOnly) {
        logger.entry(privacyPeersOnly);
        if (privacyPeersOnly) {
            List<String> ls = getConfiguredPrivacyPeerIDs();
            ls.remove(this.myId);
            return logger.exit(ls);
        } else {
            List<String> ls = getConfiguredInputPeerIDs();
            ls.remove(this.myId);
            return logger.exit(ls);
        }
    }

    public List<String> getConfiguredInputPeerIDs() {
        logger.entry();
        final List<String> inputPeerIDs = new ArrayList<String>();
        final Properties props = Configuration.getInstance(this.myId)
                .getProperties();
        final String propInputPeers = (String) props
                .get(Configuration.PROP_ACTIVE_INPUT_PEERS);
        if (propInputPeers.length() == 0) {
            return inputPeerIDs;
        }
        final String[] parts = propInputPeers.split(";");
        for (final String part : parts) {
            final String[] pps = part.split(" ");
            inputPeerIDs.add(pps[0]);
            logger.info("IP: " + pps[0]);
        }
        Collections.sort(inputPeerIDs);
        return logger.exit(inputPeerIDs);
    }

    public List<String> getConfiguredPrivacyPeerIDs() {
        logger.entry();
        final List<String> privacyPeerIDs = new ArrayList<String>();
        final Properties props = Configuration.getInstance(this.myId)
                .getProperties();
        final String propPrivacyPeers = (String) props
                .get(Configuration.PROP_ACTIVE_PRIVACY_PEERS);
        logger.info("propPrivacyPeers :=" + propPrivacyPeers
                + " with length := " + propPrivacyPeers.length());
        if (propPrivacyPeers.length() == 0) {
            return privacyPeerIDs;
        }
        final String[] parts = propPrivacyPeers.split(";");
        for (final String part : parts) {
            final String[] pps = part.split(" ");
            privacyPeerIDs.add(pps[0]);
            logger.info("PP: " + pps[0]);
        }
        Collections.sort(privacyPeerIDs);
        return logger.exit(privacyPeerIDs);
    }

    public BlockingQueue<Object> getInputQueue(final String senderId) {
        return this.inputQueues.get(senderId);
    }

    public int getNumberOfConnectedPeers(final boolean privacyPeersOnly) {
        return getActivePeers(privacyPeersOnly).size();
    }

    public OutputStrategy getOutputStrategy(final String recipientId) {
        return this.outputQueues.get(recipientId);
    }

    public Object receive(final String sender) {
        logger.info(this.myId + " wait for " + sender);
        final BlockingQueue<Object> queue = this.inputQueues.get(sender);
        try {
            Object obj = queue.take();
            logger.info(this.myId + " received obj from " + sender);
            return obj;
        } catch (final InterruptedException e) {
            logger.catching(e);
            return null;
        }
    }

    public void send(final String recipient, final Object obj) {
        logger.info("Send from " + this.myId + " to " + recipient);
        final OutputStrategy queue = this.outputQueues.get(recipient);
        try {
            queue.send(obj);
        } catch (final InterruptedException e) {
            logger.catching(e);
        }
    }

}
