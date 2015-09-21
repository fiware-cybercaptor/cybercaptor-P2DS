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

import java.io.FileNotFoundException;

import ch.ethz.sepia.mpc.additive.AdditivePeer;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.connections.ConnectionManager;

/**
 * Input peer for the entropy protocol.
 * @author martibur
 *
 */
public class EntropyPeer extends AdditivePeer {

	public EntropyPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);
	}
	
    /**
     * Write the output to a file.
     * @throws FileNotFoundException
     */
    protected void writeOutputToFile() throws FileNotFoundException {
		// The entropy values was returned per mille. Set the aggregated value correctly.
		long[] output = data.getOutput();
		data.setAggregatedOutput(((double)output[0])/1000.0);
		super.writeOutputToFile();
    }

}
