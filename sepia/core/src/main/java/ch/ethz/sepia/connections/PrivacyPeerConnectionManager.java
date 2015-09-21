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

import java.io.IOException;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.net.ssl.SSLContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.services.Utils;

/**
 * Manages all the connections of a single privacy peer.
 *
 * Incoming connections are handled by a separate {@link ConnectionAcceptor}
 * thread. To start/stop the connection acceptor thread call {@link #start()}/
 * {@link #stop()}. The ConnectionManager is an observer of the connection
 * acceptor thread and gets notified upon a new socket connection. New
 * connections are then put in the temporary connection pool. Connections for
 * which this privacy peer takes the initiative (see
 * {@link #doITakeInitiative(String)} are established in
 * {@link #establishConnections()}.
 *
 * @author martibur
 */

public class PrivacyPeerConnectionManager extends ConnectionManager implements
        Observer {
    private static final Logger logger = LogManager
            .getLogger(PrivacyPeerConnectionManager.class);

    /**
     * Decides who is initiating a connection between two privacy peers based on
     * the lexicographical order of the two IDs. The smaller ID initiates the
     * connection and sends first when data is exchanged.
     *
     * @param id1
     *            the first ID
     * @param id2
     *            the second ID
     * @return <code>true</code> if id1 sends first.
     */
    public static boolean sendingFirst(final String id1, final String id2) {
        return id1.compareTo(id2) < 0;
    }

    private ConnectionAcceptor ca;

    private final int listeningPort;

    private boolean started;

    /**
     * See {@link ConnectionManager#ConnectionManager(String, List, SSLContext)}
     * .
     *
     * @param listeningPort
     *            The server port used for incoming connections.
     */
    public PrivacyPeerConnectionManager(final String myId,
            final int listeningPort,
            final List<PrivacyPeerAddress> privacyPeerAddresses,
            final SSLContext sslContext) {
        super(myId, privacyPeerAddresses, sslContext);
        this.listeningPort = listeningPort;
        started = false;
    }

    protected boolean doITakeInitiative(final String otherId) {
        return sendingFirst(myId, otherId);
    }

    /**
     * Attempts to (re-)open all connections to privacy peers <b>for which we
     * are responsible to initiate the connection</b> (see
     * {@link #doITakeInitiative(String)}. A connection to a specific endpoint
     * is only initiated if no connection (or no working connection) is
     * currently available. Input peers and privacy peers for which we do not
     * take the initiative will connect to us.
     */
    public void establishConnections() {
        if (!started) {
            return;
        }
        for (final PrivacyPeerAddress ppa : privacyPeerAddresses) {
            if (doITakeInitiative(ppa.id)) {
                connectToPrivacyPeer(ppa);
            }
        }
    }

    /**
     * Returns whether the connection accepting thread is started.
     *
     * @return true if the thread is running
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Starts listening for incoming connections.
     */
    public void start() {
        if (!started) {
            logger.info("Privacy Peer (id=" + myId
                    + ") accepts connections on port " + listeningPort + ".");

            ca = new ConnectionAcceptor(listeningPort, sslContext);
            ca.addObserver(this);
            final Thread th = new Thread(ca, "Connection Acceptor");
            th.start();
            started = true;
        }
    }

    /**
     * Stops listening for incoming connections.
     */
    public void stop() {
        if (started) {
            logger.info("Privacy Peer (id=" + myId
                    + ") stops listening on port " + listeningPort + ".");
            ca.stopAccepting();
            ca = null;
            started = false;
        }
    }

    /**
     * This method handles notifications from the connection accepting thread.
     *
     * @param obs
     *            The observable (the connection thread)
     * @param obj
     *            The new socket connection
     */
    @Override
    public void update(final Observable obs, final Object obj) {
        if (obj instanceof Connection) {
            // We got a new incoming connection
            final Connection socket = (Connection) obj;

            // Read the ID of our new friend.
            try {
                // printSSLSocketInfo(socket);
                // socket.startHandshake();
                // TODO: do we need checkCertificate(socket);

                // Possibly try this in a loop until no more
                // InterruptedException
                final String id = (String) receiveMessage(socket);
                addTemporaryConnection(id, socket);
                sendMessage(socket, myId);

                logger.info("(" + myId + ") New connection from id " + id + ".");
            } catch (final IOException | InterruptedException e) {
                logger.error(Utils.getStackTrace(e));
            } catch (final ClassNotFoundException e) {
                logger.error(Utils.getStackTrace(e));
            }
        }
    }

}
