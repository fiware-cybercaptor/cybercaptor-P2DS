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
import java.util.Random;

import ch.ethz.sepia.startup.Configuration;

/**
 * Test cases for the Multiplication operation.
 * 
 * @author Dilip Many
 * 
 */
public class MultiplicationTest extends OperationsTest {
	/**
	 * Similar to {@link #testMultiply()}, but simulates the loss of messages.
	 */
	public void testLossyMultiply() {

		// Configure multiplications for share synchronization
		Properties props = new Properties();
		props.setProperty(Configuration.PROP_SYNCHRONIZE_SHARES,
				Boolean.toString(true));
		Configuration.getInstance("default-test").setProperties(props);

		for (long fieldSize2 : this.fieldSizes) {
			this.fieldSize = fieldSize2;

			// Choosing degree t=1 allows for some missing shares
			initializeMpcShamirSharingInstances(1);
			initializeMpcShamirSharingProtocolPrimitives(1);

			createInputValues();
			createInputShares();
			System.out
					.println("\ntesting multiplication operation with group order="
							+ this.fieldSize);

			// multiply inputs
			int[] operationIDs = new int[this.numberOfPeers * this.input.length
					* this.input.length];
			int nextID = 0;
			long[] data = null;
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				this.primitives[privacyPeerIndex]
						.initialize(operationIDs.length);
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[2];
							data[0] = this.inputShares[peerIndex][privacyPeerIndex][inputIndex];
							data[1] = this.inputShares[peerIndex][privacyPeerIndex][inputIndex2];
							this.primitives[privacyPeerIndex].multiply(nextID,
									data);
						}
					}
				}
			}

			// Now pick the failing PP
			Random rand = new Random();
			int failingPP = rand.nextInt(this.numberOfPrivacyPeers);
			doLossyOperation(operationIDs, failingPP, false);

			// From now on, the failing PP is considered offline...

			// get multiplication results
			long[][][] multiplicationResults = new long[this.numberOfPrivacyPeers][this.input.length][this.input.length];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							multiplicationResults[privacyPeerIndex][inputIndex][inputIndex2] = this.primitives[privacyPeerIndex]
									.getResult(nextID)[0];
						}
					}
				}
			}

			// reconstruct multiplication results
			operationIDs = new int[this.numberOfPeers * this.input.length
					* this.input.length];
			nextID = 0;
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				this.primitives[privacyPeerIndex]
						.initialize(operationIDs.length);
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[1];
							data[0] = multiplicationResults[privacyPeerIndex][inputIndex][inputIndex2];
							this.primitives[privacyPeerIndex].reconstruct(
									nextID, data);
						}
					}
				}
			}
			doLossyOperation(operationIDs, failingPP, true);

			// get results
			long[][][] computedResults = new long[this.numberOfPrivacyPeers][this.input.length][this.input.length];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							computedResults[privacyPeerIndex][inputIndex][inputIndex2] = this.primitives[privacyPeerIndex]
									.getResult(nextID)[0];
						}
					}
				}
			}

			// assert that results of reconstruction are equal to original input
			long realResult = 0;
			BigInteger bigA = null;
			BigInteger bigB = null;
			BigInteger bigGroupOrder = BigInteger.valueOf(this.fieldSize);
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				// ignore the failed PP
				if (privacyPeerIndex == failingPP) {
					continue;
				}
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							bigA = BigInteger.valueOf(this.input[inputIndex]);
							bigB = BigInteger.valueOf(this.input[inputIndex2]);
							realResult = bigA.multiply(bigB).mod(bigGroupOrder)
									.longValue();
							assertEquals(
									"checking product of "
											+ this.input[inputIndex] + "*"
											+ this.input[inputIndex2] + " mod "
											+ this.fieldSize + ": ",
									realResult,
									computedResults[privacyPeerIndex][inputIndex][inputIndex2]);
						}
					}
				}
			}
		}
	}

	/**
	 * Test method for
	 * {@link mpc.protocolPrimitives.Primitives#multiply(int, long[])}.
	 * <p>
	 * Tests if result of MPC multiplication is equal to result of normal
	 * multiplication.
	 */
	public void testMultiply() {
		for (long fieldSize2 : this.fieldSizes) {
			this.fieldSize = fieldSize2;
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out
					.println("\ntesting multiplication operation with group order="
							+ this.fieldSize);

			// multiply inputs
			int[] operationIDs = new int[this.numberOfPeers * this.input.length
					* this.input.length];
			int nextID = 0;
			long[] data = null;
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				this.primitives[privacyPeerIndex]
						.initialize(operationIDs.length);
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[2];
							data[0] = this.inputShares[peerIndex][privacyPeerIndex][inputIndex];
							data[1] = this.inputShares[peerIndex][privacyPeerIndex][inputIndex2];
							this.primitives[privacyPeerIndex].multiply(nextID,
									data);
						}
					}
				}
			}
			doOperation(operationIDs);

			// get multiplication results
			long[][][] multiplicationResults = new long[this.numberOfPrivacyPeers][this.input.length][this.input.length];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							multiplicationResults[privacyPeerIndex][inputIndex][inputIndex2] = this.primitives[privacyPeerIndex]
									.getResult(nextID)[0];
						}
					}
				}
			}

			// reconstruct multiplication results
			operationIDs = new int[this.numberOfPeers * this.input.length
					* this.input.length];
			nextID = 0;
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				this.primitives[privacyPeerIndex]
						.initialize(operationIDs.length);
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[1];
							data[0] = multiplicationResults[privacyPeerIndex][inputIndex][inputIndex2];
							this.primitives[privacyPeerIndex].reconstruct(
									nextID, data);
						}
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][] computedResults = new long[this.numberOfPrivacyPeers][this.input.length][this.input.length];
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							nextID = peerIndex * this.input.length
									* this.input.length + inputIndex
									* this.input.length + inputIndex2;
							computedResults[privacyPeerIndex][inputIndex][inputIndex2] = this.primitives[privacyPeerIndex]
									.getResult(nextID)[0];
						}
					}
				}
			}

			// assert that results of reconstruction are equal to original input
			long realResult = 0;
			BigInteger bigA = null;
			BigInteger bigB = null;
			BigInteger bigGroupOrder = BigInteger.valueOf(this.fieldSize);
			for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
				for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
					for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
						for (int inputIndex2 = 0; inputIndex2 < this.input.length; inputIndex2++) {
							bigA = BigInteger.valueOf(this.input[inputIndex]);
							bigB = BigInteger.valueOf(this.input[inputIndex2]);
							realResult = bigA.multiply(bigB).mod(bigGroupOrder)
									.longValue();
							assertEquals(
									"checking product of "
											+ this.input[inputIndex] + "*"
											+ this.input[inputIndex2] + " mod "
											+ this.fieldSize + ": ",
									realResult,
									computedResults[privacyPeerIndex][inputIndex][inputIndex2]);
						}
					}
				}
			}
		}
	}

}
