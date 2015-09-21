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

package ch.ethz.sepia.protocolPrimitives.operations;

import java.math.BigInteger;
import java.util.Properties;

import ch.ethz.sepia.startup.Configuration;

/**
 * Test cases for the Product operation.
 * 
 * @author Dilip Many
 * 
 */
public class ProductTest extends OperationsTest {
	/**
	 * computes the product of all input values (mod fieldSize)
	 * 
	 * @return the product of all input values (mod fieldSize)
	 */
	private long computInputProduct() {
		BigInteger bigGroupOrder = BigInteger.valueOf(this.fieldSize);
		BigInteger bigA = BigInteger.valueOf(this.input[0]);
		BigInteger bigB = null;
		for (int i = 1; i < this.input.length; i++) {
			bigB = BigInteger.valueOf(this.input[i]);
			bigA = bigA.multiply(bigB).mod(bigGroupOrder);
		}
		return bigA.longValue();
	}

	/**
	 * Test method for
	 * {@link mpc.protocolPrimitives.Primitives#product(int, long[])}.
	 * <p>
	 * Tests if result of MPC product is equal to result of normal
	 * multiplication.
	 */
	public void testProduct() {
		// Configure multiplications for share synchronization
		Properties props = new Properties();
		props.setProperty(Configuration.PROP_SYNCHRONIZE_SHARES,
				Boolean.toString(true));
		Configuration.getInstance("default-test").setProperties(props);

		for (long fieldSize2 : this.fieldSizes) {
			this.fieldSize = fieldSize2;
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting product operation with group order="
					+ this.fieldSize);

			// compute product of inputs
			int[] operationIDs = new int[this.numberOfPeers];
			long[] data = null;
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				this.primitives[privacyPeerIndex]
						.initialize(operationIDs.length);
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					// data = new long[input.length];
					// for(int inputIndex = 0; inputIndex < input.length;
					// inputIndex++) {
					// data[inputIndex] =
					// inputShares[peerIndex][privacyPeerIndex][inputIndex];
					// }
					operationIDs[peerIndex] = peerIndex;
					this.primitives[privacyPeerIndex].product(peerIndex,
							this.inputShares[peerIndex][privacyPeerIndex]);
				}
			}
			doOperation(operationIDs);

			// get product results
			long[][] productResults = new long[this.numberOfPrivacyPeers][this.numberOfPeers];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					productResults[privacyPeerIndex][peerIndex] = this.primitives[privacyPeerIndex]
							.getResult(peerIndex)[0];
				}
			}

			// reconstruct product results
			operationIDs = new int[operationIDs.length];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				this.primitives[privacyPeerIndex]
						.initialize(operationIDs.length);
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					operationIDs[peerIndex] = peerIndex;
					data = new long[1];
					data[0] = productResults[privacyPeerIndex][peerIndex];
					this.primitives[privacyPeerIndex].reconstruct(peerIndex,
							data);
				}
			}
			doOperation(operationIDs);

			// get results
			long[][] computedResults = new long[this.numberOfPrivacyPeers][this.numberOfPeers];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					computedResults[privacyPeerIndex][peerIndex] = this.primitives[privacyPeerIndex]
							.getResult(peerIndex)[0];
				}
			}

			// assert that results of reconstruction are equal to original input
			long realResult = computInputProduct();
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					assertEquals("checking product of peer " + peerIndex
							+ " in field of size " + this.fieldSize + ": ",
							realResult,
							computedResults[privacyPeerIndex][peerIndex]);
				}
			}
		}
	}
}
