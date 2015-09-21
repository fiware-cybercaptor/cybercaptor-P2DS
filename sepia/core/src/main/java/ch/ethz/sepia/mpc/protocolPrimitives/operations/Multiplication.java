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

import java.util.Properties;

import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesException;
import ch.ethz.sepia.mpc.protocolPrimitives.operationStates.MultiplicationState;
import ch.ethz.sepia.startup.Configuration;

/**
 * Multiplication Operation, implemented according to:
 * <p/>
 * <i>R. Gennaro, M. Rabin, and T. Rabin. Simplified VSS and fast-track
 * multiparty computations with applications to threshold cryptography. In 7th
 * annual ACM symposium on Principles of distributed computing (PODC), 1998.</i>
 * 
 * @author Dilip Many, Martin Burkhart
 * 
 */
public class Multiplication extends MultiplicationState implements IOperation {

	protected long[] backupShares;
	protected boolean synchronizationDone = false;

	/**
	 * When the evaluation points are interpolated in step 2, it is important
	 * that all privacy peers use the same set of shares. Otherwise, they don't
	 * arrive at the same polynomial representing the secret product. In case a
	 * privacy peer crashed during the message exchange, PP1 might have gotten
	 * its share while PP2 might have a missing share. If this flag is
	 * <code>true</code>, the PPs synchronize missing shares before
	 * interpolation. Note that this slows down the computation. So if you don't
	 * expect failures, set it to <code>false</code>. Note that in a worst case,
	 * an additional failure during the synchronization of missing shares could
	 * still lead to inconsistent configurations. We accept this risk (it's very
	 * unlikely). After all, we can not solve the consensus problem. Here,
	 * asynchronous MPC would be required.
	 */
	protected boolean synchronizeMissingShares = false;

	/**
	 * creates a multiplication sub-operation.
	 * 
	 * @param data
	 *            array containing the 2 shares to multiply
	 */
	public Multiplication(final long[] data) {
		// store initial arguments
		setInitialData(data);

		/**
		 * -- munt
		 * 
		 * set this always to true unless testing. According to the docs missing
		 * shares will result in inconsistencies. However, the real reason why
		 * we do this is because we have no reference to peerName in this class
		 * so we can't recall the correct configuration for this particular peer
		 * at this moment.
		 */

		// The synchronization of missing shares is controlled via a property.

		Properties props = Configuration.getInstance("default-test")
				.getProperties();
		if (props != null) {
			String synchShares = props
					.getProperty(Configuration.PROP_SYNCHRONIZE_SHARES);
			if (synchShares != null) {
				this.synchronizeMissingShares = Boolean
						.parseBoolean(synchShares);
			}
		} else {
			this.synchronizeMissingShares = true;
		}

		return;
	}

	protected void backupShares() {
		long[] shares = getSharesFromPrivacyPeers();
		this.backupShares = new long[shares.length];
		for (int i = 0; i < shares.length; i++) {
			this.backupShares[i] = shares[i];
		}
	}

	/**
	 * do the next step of the multiplication operation
	 * 
	 * @param primitives
	 *            the protocol primitives.
	 * @throws PrimitivesException
	 */
	@Override
	public void doStep(final Primitives primitives) throws PrimitivesException {
		ShamirSharing mpcShamirSharing = primitives.getMpcShamirSharing();

		// step1: multiply shares and generate truncation shares
		if (getCurrentStep() == 1) {
			// generate multipliedTruncationShares
			long[] data = getInitialData();
			setSharesForPrivacyPeers(mpcShamirSharing
					.generateShare(mpcShamirSharing.modMultiply(data[0],
							data[1])));
			copyOwnShares(primitives.getMyPrivacyPeerIndex());
			return;
		}

		// step2: synchronize missing shares, if required. Then do
		// interpolation.
		if (getCurrentStep() == 2) {
			if (this.synchronizeMissingShares && !this.synchronizationDone) {
				// step 2a) Here we misuse the "shares" as a container to
				// transport information about missing shares
				backupShares();

				/*
				 * We encode the information about missing shares in an integer.
				 * In the bitwise representation, "1" indicates an available
				 * share, "0" a missing share. Note: For this to work, log2(p)>m
				 * must hold!
				 */
				long[] shares = getSharesFromPrivacyPeers();
				long inventory = 0;
				long positionValue = 1;
				for (long share : shares) {
					if (share != ShamirSharing.MISSING_SHARE) {
						inventory += positionValue;
					}
					positionValue = 2 * positionValue;
				}

				// Send the same information to all privacy peers
				long[] inventories = new long[shares.length];
				for (int i = 0; i < shares.length; i++) {
					inventories[i] = inventory;
				}
				setSharesForPrivacyPeers(inventories);
				copyOwnShares(primitives.getMyPrivacyPeerIndex());
				this.synchronizationDone = true;
			} else {
				// step 2b)
				long[] shares;
				if (this.synchronizeMissingShares) {
					shares = this.backupShares;

					// Compute the intersection of available shares
					// Note: From a disconnected privacy peer, we get an
					// inventory of value ShamirSharing.MISSING_SHARE.
					long[] inventories = getSharesFromPrivacyPeers();
					long aggregateInventory = -1;
					for (long inventorie : inventories) {
						if (inventorie != ShamirSharing.MISSING_SHARE) {
							if (aggregateInventory == -1) {
								aggregateInventory = inventorie;
							} else {
								aggregateInventory &= inventorie;
							}
						}
					}

					// Now delete the shares that were not received by all PPs.
					long positionValue = 1;
					for (int i = 0; i < shares.length; i++) {
						if ((aggregateInventory & positionValue) == 0) {
							shares[i] = ShamirSharing.MISSING_SHARE;
						}
						positionValue = 2 * positionValue;
					}
				} else {
					shares = getSharesFromPrivacyPeers();
				}

				// Finally interpolate the product.
				long[] result = new long[1];
				result[0] = mpcShamirSharing.interpolate(shares, true);
				setFinalResult(result);
			}
			return;
		}

		// step3: Nothing

		return;
	}
}
