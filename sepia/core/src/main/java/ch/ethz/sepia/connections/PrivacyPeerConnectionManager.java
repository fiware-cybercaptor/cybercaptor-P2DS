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

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

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

public class PrivacyPeerConnectionManager extends ConnectionManager {
    @SuppressWarnings("unused")
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(PrivacyPeerConnectionManager.class));

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

    public PrivacyPeerConnectionManager(final String myId) {
        super(myId);
    }

}
