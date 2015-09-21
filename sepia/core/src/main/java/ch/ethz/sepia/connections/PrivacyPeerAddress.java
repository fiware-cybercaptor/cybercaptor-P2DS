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

/**
 * Simple container class for privacy peer information.
 * 
 * @author martibur
 */
public class PrivacyPeerAddress implements Comparable<PrivacyPeerAddress> {
    /**
     * The host of the privacy peer.
     */
    public String host;
    /**
     * The ID of the privacy peer.
     */
    public String id;
    /**
     * The server port of the privacy peer.
     */
    public int serverPort;

    /**
     * for url based connections
     */
    public String url;

    public PrivacyPeerAddress(final String id, final String url) {
        this.id = id;
        this.url = url;
    }

    /**
     * Constructs a PrivacyPeerAddress object.
     * 
     * @param id
     *            the privacy peer ID
     * @param host
     *            the privacy peer host
     * @param serverPort
     *            the privacy peer server port
     */
    public PrivacyPeerAddress(final String id, final String host,
            final int serverPort) {
        this.id = id;
        this.host = host;
        this.serverPort = serverPort;
    }

    /**
     * Compares the IDs of the two privacy peer addresses.
     */
    @Override
    public int compareTo(final PrivacyPeerAddress o) {
        return this.id.compareTo(o.id);
    }

    public boolean isURLBased() {
        return this.url != null;
    }

    @Override
    public String toString() {
        return this.id + " (" + this.host + ":" + this.serverPort + ")";
    }
}
