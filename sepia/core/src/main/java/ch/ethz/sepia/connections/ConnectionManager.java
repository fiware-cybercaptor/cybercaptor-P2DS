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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.services.Utils;

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
    private static final Logger logger = LogManager
            .getLogger(ConnectionManager.class);

    private static final int SHORT_BREAK_MS = 2000;

    private static CommunicationStatistics statistics = new CommunicationStatistics();


    public static void logStatistics() {
        statistics.logStatistics();
    }

    public static void newStatisticsRound() {
        statistics.newStatisticsRound();
    }

    protected HashMap<String, Connection> activeConnectionPool;

    protected int activeTimeout = 10000;

    protected int minInputPeers = 0;

    protected int minPrivacyPeers = 3;

    protected String myId;

    protected List<PrivacyPeerAddress> privacyPeerAddresses;
    /**
     * Sorted list of privacy peer IDs. Derived from privacyPeerAddresses.
     */
    protected List<String> privacyPeerIDs;

    /**
     * SSL sockets created with the default SSL socket factories will not have
     * any certificates associated to authenticate them. In order to associate
     * certificates with SSL sockets, we need to use the SSLContext class to
     * create SSL socket factories.
     */
    protected SSLContext sslContext;

    protected HashMap<String, Connection> temporaryConnectionPool;

    protected boolean useCompression = false;

    /**
     * Constructor for the connection manageer.
     *
     * @param myId
     *            My own ID.
     * @param privacyPeerAddresses
     *            All the privacy peers
     * @param sslContext
     *            An SSLContext that was initialized for client authentication.
     *            That is, the certificates from the local keystore were
     *            associated with it.
     */
    public ConnectionManager(final String myId,
            final List<PrivacyPeerAddress> privacyPeerAddresses,
            final SSLContext sslContext) {
        this.myId = myId;
        this.privacyPeerAddresses = privacyPeerAddresses;
        activeConnectionPool = new HashMap<String, Connection>();
        temporaryConnectionPool = new HashMap<String, Connection>();
        this.sslContext = sslContext;

        // Create a local list of all the privacy peer IDs for easy matching.
        privacyPeerIDs = new ArrayList<String>();
        for (final PrivacyPeerAddress ppa : privacyPeerAddresses) {
            privacyPeerIDs.add(ppa.id);
        }
        Collections.sort(privacyPeerIDs);
    }

    /**
     * Copies temporary connections to the active connection pool. Call this
     * method before a new communication round starts. Note: This method needs
     * to be called at least once before messages can be sent.
     */
    public synchronized void activateTemporaryConnections() {
        activeConnectionPool.putAll(temporaryConnectionPool);
        temporaryConnectionPool.clear();
    }

    /**
     * Adds an entry to the temporary connection pool. Since the main thread
     * might simultaneously count the number of peers, it is synchronized to
     * avoid a {@link ConcurrentModificationException}.
     *
     * @param hostId
     *            the ID of the connected host.
     * @param socket
     *            the socket
     * @throws PrivacyViolationException
     */
    protected synchronized void addTemporaryConnection(final String hostId,
            final Connection socket) {
        temporaryConnectionPool.put(hostId, socket);
    }

    /**
     * Connects to an SSL server.
     *
     * @param host
     *            the host address
     * @param serverPort
     *            the server port
     * @return an SSL socket
     * @throws UnknownHostException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    /*
    protected Connection createConnection(final String host, final int serverPort)
            throws UnknownHostException, IOException, NoSuchAlgorithmException {
        final SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        final SSLSocket socket = (SSLSocket) socketFactory.createSocket(
                InetAddress.getByName(host), serverPort);
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
        socket.setNeedClientAuth(true);
        socket.addHandshakeCompletedListener(event -> logger
                .info("SSL handshake is completed. Chosen ciphersuite: "
                        + event.getCipherSuite()));
        checkCertificate(socket);
        return socket;
    }
    */

    /**
     * Close all connections.
     */
    public synchronized void closeConnections() {
        for (final Connection socket : activeConnectionPool.values()) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException e1) {
                    // ignore
                }
            }
        }
        activeConnectionPool.clear();

        for (final Connection socket : temporaryConnectionPool.values()) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException e1) {
                    // ignore
                }
            }
        }
        temporaryConnectionPool.clear();

    }

    /**
     * Tries to connect to a privacy peer if no suitable connection is already
     * available.
     *
     * @param ppa
     *            the privacy peer address
     */
    protected void connectToPrivacyPeer(final PrivacyPeerAddress ppa) {
        // Do we already have an active connection?
        Connection socket = activeConnectionPool.get(ppa.id);
        if (socket != null && !socket.isConnected()) {
            // We have a malfunctioning socket. Close it now and open a new one
            // below
            try {
                socket.close();
            } catch (final IOException e) {
                // ignore
            }
            socket = null;
        }

        // Do we already have a temporary connection?
        if (socket == null) {
            socket = temporaryConnectionPool.get(ppa.id);
            if (socket != null && !socket.isConnected()) {
                // We have a malfunctioning socket. Close it now and open a new
                // one below
                try {
                    socket.close();
                } catch (final IOException e) {
                    // ignore
                }
                socket = null;
            }
        }

        if (socket == null) {
            // no socket available. Try to create a new one.
            try {
                socket = ConnectionFactory.createConnection(ppa, sslContext);
                sendMessage(socket, myId);
                // Possibly try in a loop until no more InterruptedException
                final String ppId = (String) receiveMessage(socket);
                assert ppId.equals(ppa.id);
                temporaryConnectionPool.put(ppa.id, socket);

                logger.info("(" + myId + ") Established connection to PP "
                        + ppa.id + " (" + ppa.host + ":" + ppa.serverPort + ")");
            } catch (final SocketException | InterruptedException e) {
                logger.warn("(" + myId + ") Host " + ppa.id
                        + " not reachable on " + ppa.host + ":"
                        + ppa.serverPort + ". Details: " + e.getMessage());
            } catch (final UnknownHostException e) {
                logger.error(Utils.getStackTrace(e));
            } catch (final IOException e) {
                logger.error(Utils.getStackTrace(e));
            } catch (final ClassNotFoundException e) {
                logger.error(Utils.getStackTrace(e));
            } catch (final NoSuchAlgorithmException e) {
                logger.error(Utils.getStackTrace(e));
            }
        }
    }

    /**
     * (Re-)establish connections to endpoints for which we take the initiative.
     * Input peers connect to the privacy peers and privacy peers to each other.
     * New connections are stored in the temporary connection pool.
     */
    public abstract void establishConnections();

    /**
     * Returns all active connections.
     *
     * @return the active connection pool.
     */
    public HashMap<String, Connection> getActiveConnectionPool() {
        return activeConnectionPool;
    }

    /**
     * Returns a sorted list of active peer IDs.
     *
     * @param wantPrivacyPeers
     *            <code>true</code> for privacy peers, <code>false</code> for
     *            input peers.
     * @return the active peer IDs
     */
    public synchronized List<String> getActivePeers(
            final boolean wantPrivacyPeers) {
        final ArrayList<String> idList = new ArrayList<String>();
        final HashSet<String> ids = new HashSet<String>(
                activeConnectionPool.keySet());
        for (final String id : ids) {
            final boolean isPrivacyPeer = privacyPeerIDs.contains(id);
            if (isPrivacyPeer && wantPrivacyPeers || !isPrivacyPeer
                    && !wantPrivacyPeers) {
                idList.add(id);
            }
        }
        Collections.sort(idList);
        return idList;
    }

    /**
     * Gets the active timeout.
     *
     * @return the active timeout in milliseconds
     */
    public int getActiveTimeout() {
        return activeTimeout;
    }

    /**
     * Gets a sorted list of all <b>configured</b> privacy peer IDs. Note that
     * not all of these are necessarily online. To get active IDs, call
     * {@link #getActivePeers(boolean)}.
     *
     * @return all privacy peer IDs.
     */
    public List<String> getConfiguredPrivacyPeerIDs() {
        return privacyPeerIDs;
    }

    public Connection getConnection(final String hostId) {
        return activeConnectionPool.get(hostId);
    }

    /**
     * Gets the minimum number of required input peers.
     *
     * @return minimum number of required input peers
     */
    public int getMinInputPeers() {
        return minInputPeers;
    }

    /**
     * Gets the minimum number of required privacy peers.
     *
     * @return minimum number of required privacy peers
     */
    public int getMinPrivacyPeers() {
        return minPrivacyPeers;
    }

    /**
     * Returns my Id.
     *
     * @return the Id
     */
    public String getMyId() {
        return myId;
    }


    /**
     * Counts the number of connected input or privacy peers.
     *
     * @param countPrivacyPeers
     *            <code>true</code> for counting privacy peers,
     *            <code>false</code> for counting input peers.
     * @param activeOnly
     *            only considers connections in the active connection pool.
     *
     * @return the total number of connected peers
     */
    public synchronized int getNumberOfConnectedPeers(
            final boolean countPrivacyPeers, final boolean activeOnly) {
        int count = 0;
        final HashSet<String> ids = new HashSet<String>(
                activeConnectionPool.keySet());
        if (!activeOnly) {
            ids.addAll(temporaryConnectionPool.keySet());
        }

        for (final String id : ids) {
            final boolean isPrivacyPeer = privacyPeerIDs.contains(id);
            if (isPrivacyPeer && countPrivacyPeers || !isPrivacyPeer
                    && !countPrivacyPeers) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns all temporary connections.
     *
     * @return the temporary connection pool.
     */
    public HashMap<String, Connection> getTemporaryConnectionPool() {
        return temporaryConnectionPool;
    }

    /**
     * Receives messages over the socket connection
     *
     * @return The message that was received
     * @throws ClassNotFoundException
     * @throws InterruptedException
     */
    public Object receiveMessage(final Connection socket) throws IOException,
            ClassNotFoundException, InterruptedException {
        final byte[] messageBufferTempResult = socket.read();

        // decompress and deserialize message
        final ByteArrayInputStream byteInStream = new ByteArrayInputStream(
                messageBufferTempResult);
        ObjectInputStream objInStream = null;
        Inflater inflater = null;
        if (useCompression) {
            inflater = new Inflater();
            final InflaterInputStream infInStream = new InflaterInputStream(
                    byteInStream, inflater);
            objInStream = new ObjectInputStream(infInStream);
        } else {
            objInStream = new ObjectInputStream(byteInStream);
        }
        final Object message = objInStream.readObject();

        // Update connection statistics
        statistics.incMessagesReceived(messageBufferTempResult.length);
        if (useCompression) {
            statistics.incUncompressedBytesReceived(inflater.getTotalOut());
        }

        return message;
    }

    /**
     * Received a message from a sender. A connection to the sender must be
     * available in the connection pool. In case an exception occurs, the
     * corresponding connection is removed from the active connection pool.
     *
     * @param senderId
     *            the ID of the sender.
     * @return The received message object. If any problem occurred or no
     *         connection is available, null is returned.
     * @throws PrivacyViolationException
     */
    public Object receiveMessage(final String senderId)
            throws PrivacyViolationException {
        Object msg = null;
        boolean received = false;

        final Connection socket = activeConnectionPool.get(senderId);
        if (socket != null && socket.isConnected()) {
            try {
                // Possibly have to try in a loop until no more
                // InterruptedException --neuhaus
                msg = receiveMessage(socket);
                received = true;
            } catch (final IOException | InterruptedException e) {
                logger.warn("(" + myId
                        + ") Exception when receiving message from "
                        + senderId + ". Details: " + e.getMessage());

                // Remove the broken connection
                try {
                    socket.close();
                } catch (final IOException e1) {
                    // ignore
                }
                removeActiveConnection(senderId);
            } catch (final ClassNotFoundException e) {
                logger.fatal(Utils.getStackTrace(e));
            }
        }

        if (!received) {
            logger.warn("(" + myId
                    + ") No connection available to receive message from "
                    + senderId + ".");
        }

        return msg;
    }

    /**
     * Removes an entry from the active connection pool. Since this might be
     * called simultaneously by multiple threads, it is synchronized to avoid a
     * {@link ConcurrentModificationException}.
     *
     * @param hostId
     *            the ID of the connected host.
     * @throws PrivacyViolationException
     */
    protected synchronized void removeActiveConnection(final String hostId)
            throws PrivacyViolationException {
        activeConnectionPool.remove(hostId);
        verifyPrivacy();
    }

    /**
     * Send a message over the socket
     *
     * @param message
     *            the message to be sent
     */
    public void sendMessage(final Connection socket, final Object object)
            throws IOException {
        /*
         * A direct composition of the ObjectOutpuputStream,
         * DeflaterOutputStream, and socket.getOutputStream() lead to occasional
         * "broken pipe" and "unknown compression algorithm" exceptions. A safer
         * way, which also allows to collect statistics, is to first serialize
         * and compress the object locally and then send the message as a byte
         * array.
         */

        // First serialize and (optionally) compress the message
        final ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        ObjectOutputStream objOutStream = null;

        Deflater deflater = null;
        DeflaterOutputStream defOutStream = null;
        if (useCompression) {
            deflater = new Deflater(Deflater.BEST_SPEED);
            deflater.setStrategy(Deflater.HUFFMAN_ONLY);
            defOutStream = new DeflaterOutputStream(byteOutStream, deflater);
            objOutStream = new ObjectOutputStream(defOutStream);
        } else {
            objOutStream = new ObjectOutputStream(byteOutStream);
        }
        objOutStream.writeObject(object);
        objOutStream.flush();
        objOutStream.close();
        if (useCompression) {
            defOutStream.finish();
            defOutStream.flush();
        }
        final byte[] byteMessage = byteOutStream.toByteArray();
        byteOutStream.close();

        socket.write(byteMessage);

        // Update connection statistics
        statistics.incMessagesSent(byteMessage.length);
        if (useCompression) {
            statistics.incUncompressedBytesSent(deflater.getTotalIn());
        }
    }

    /**
     * Sends a message to a target. A connection to the recipient must be
     * available in the connection pool. In case an exception occurs, the
     * corresponding connection is removed from the active connection pool.
     *
     * @param recipientId
     *            the ID of the recipient.
     * @param message
     *            the message
     * @return <code>true</code> if the message was sent successfully.
     *         <code>false</code> means that the message was not sent
     *         successfully, either due to an exception or because no connection
     *         to the recipient is available.
     * @throws PrivacyViolationException
     */
    public boolean sendMessage(final String recipientId, final Object message)
            throws PrivacyViolationException {
        boolean sent = false;

        final Connection socket = activeConnectionPool.get(recipientId);
        if (socket != null && socket.isConnected()) {
            try {
                sendMessage(socket, message);
                sent = true;
            } catch (final IOException e) {
                logger.warn("(" + myId
                        + ") IOException when sending message to "
                        + recipientId + ". Details: " + e.getMessage());

                // Remove the broken connection
                try {
                    socket.close();
                } catch (final IOException e1) {
                    // ignore
                }
                removeActiveConnection(recipientId);
            }
        }

        if (!sent) {
            logger.warn("(" + myId
                    + ") No connection available to deliver message to "
                    + recipientId + ". Message dropped.");
        }
        return sent;
    }

    /**
     * Sets the active timeout. This timeout is the time for which
     * {@link #waitForConnections()} waits after the last connection has been
     * established. Default is 10,000ms.
     *
     * @param the
     *            active timeout in milliseconds.
     */
    public void setActiveTimeout(final int activeTimeout) {
        this.activeTimeout = activeTimeout;
    }

    /**
     * Enables/disables compression of messages. Default is no compression.
     *
     * @param enableCompression
     *            true for enabling compression
     */
    public void setEnableCompression(final boolean enableCompression) {
        useCompression = enableCompression;
        logger.info("Using compression: " + enableCompression);
    }

    /**
     * Sets the minimum number of required input peer connections. The default
     * is 0. If run on an input peer, set this value to 0, because input peers
     * don't connect to each other.
     *
     * @param minInputPeers
     *            minimum number of required input peers
     */
    public void setMinInputPeers(final int minInputPeers) {
        this.minInputPeers = minInputPeers;
    }

    /**
     * Sets the minimum number of required privacy peer connections. The default
     * is 3. Note that for privacy peers, the value from the config file needs
     * to be decremented by 1, because they don't connect to themselves.
     *
     * @param minPrivacyPeers
     *            minimum number of required privacy peers
     */
    public void setMinPrivacyPeers(final int minPrivacyPeers) {
        this.minPrivacyPeers = minPrivacyPeers;
    }

    /**
     * Checks whether enough active input and privacy peers are connected. If
     * not, an exception is thrown. This method is called after a connection is
     * lost, to check whether we can continue.
     *
     * @throws PrivacyViolationException
     */
    protected void verifyPrivacy() throws PrivacyViolationException {
        final int activeIPs = getNumberOfConnectedPeers(false, true);
        final int activePPs = getNumberOfConnectedPeers(true, true);

        if (activeIPs < minInputPeers || activePPs < minPrivacyPeers) {
            throw new PrivacyViolationException(activeIPs, activePPs,
                    minInputPeers, minPrivacyPeers);
        }
    }

    /**
     * Repeatedly calls {@link #establishConnections()} until enough (active and
     * temporary) connections are available and the active timeout (see
     * {@link #getActiveTimeout()}) has occurred. Call this at the beginning of
     * each computation round to ensure that the privacy policy is met and to
     * allow new input/privacy peers to connect. The number of minimum input and
     * privacy peer connections can be configured using
     * {@link #setMinInputPeers(int)} and {@link #setMinPrivacyPeers(int)}.
     */
    public void waitForConnections() {
        long lastConnection = System.currentTimeMillis();
        int connectedIPs = getNumberOfConnectedPeers(false, false);
        int connectedPPs = getNumberOfConnectedPeers(true, false);

        while (connectedIPs < minInputPeers || connectedPPs < minPrivacyPeers
                || System.currentTimeMillis() - lastConnection <= activeTimeout) {
            logger.info("Waiting for more connections. (Connected input peers: "
                    + connectedIPs
                    + "/"
                    + minInputPeers
                    + ", connected privacy peers: "
                    + connectedPPs
                    + "/"
                    + minPrivacyPeers + ")");
            establishConnections();
            final int connectedIPsNow = getNumberOfConnectedPeers(false, false);
            final int connectedPPsNow = getNumberOfConnectedPeers(true, false);

            if (connectedIPsNow != connectedIPs
                    || connectedPPsNow != connectedPPs) {
                // we had a new connection. Update the timestamp.
                lastConnection = System.currentTimeMillis();
                connectedIPs = connectedIPsNow;
                connectedPPs = connectedPPsNow;
                logger.info("New connections found. (Connected input peers: "
                        + connectedIPs + ", connected privacy peers: "
                        + connectedPPs + ")");
            }

            // Take a short break
            try {
                Thread.sleep(SHORT_BREAK_MS);
            } catch (final InterruptedException e) {
                // ignore
            }
        }
        logger.info("Enough connections found and timout occurred. (Connected input peers: "
                + connectedIPs
                + ", connected privacy peers: "
                + connectedPPs
                + ")");
    }

}
