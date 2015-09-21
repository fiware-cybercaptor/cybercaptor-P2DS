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

package ch.ethz.sepia.mpc.entropy;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.mpc.additive.AdditivePeer;
import ch.ethz.sepia.services.Stopper;

/**
 * Input peer for the entropy protocol.
 * 
 * @author martibur
 * 
 */
public class EntropyPeer extends AdditivePeer {

    public EntropyPeer(final String peerName, final int myPeerIndex,
            final ConnectionManager cm, final Stopper stopper) throws Exception {
        super(peerName, myPeerIndex, cm, stopper);
    }

}
