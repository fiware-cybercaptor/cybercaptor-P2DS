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

package ch.ethz.sepia.mpc.protocolPrimitives.operations;


import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.operationStates.MultiplicationState;
import ch.ethz.sepia.services.Utils;


/**
 * Reconstruction Operation. Reconstructs a shared secret.
 *
 * @author Dilip Many
 *
 */
public class Reconstruction extends MultiplicationState implements IOperation {
    private static final XLogger logger = new XLogger(LoggerFactory.getLogger(Reconstruction.class));

	/**
	 * creates a reconstruction sub-operation.
	 *
	 * @param data	array containing the share to reconstruct.
	 */
	public Reconstruction(final long[] data) {
		// store initial arguments
		setInitialData(data);
		return;
	}


	/**
	 * do the next step of the reconstruction operation
	 */
	public void doStep(final Primitives primitives) {
		// step1: copy my share for everyone
		if(getCurrentStep() == 1) {
			final long[] shares = new long[primitives.getNumberOfPrivacyPeers()];
			for(int peerIndex = 0; peerIndex < primitives.getNumberOfPrivacyPeers(); peerIndex++) {
				shares[peerIndex] = getInitialData()[0];
			}
			setSharesForPrivacyPeers(shares);
			copyOwnShares(primitives.getMyPrivacyPeerIndex());
			return;
		}

		// step2: interpolate secret from received shares
		if(getCurrentStep() == 2) {
			final long[] result = new long[1];
			try {
				result[0] = primitives.getMpcShamirSharing().interpolate( getSharesFromPrivacyPeers(), false );
			} catch (final Exception e) {
				logger.error("interpolation of shares of reconstruction operation failed: " + Utils.getStackTrace(e));
			}
			setFinalResult(result);
			return;
		}

		// step3: operation completed

		return;
	}
}
