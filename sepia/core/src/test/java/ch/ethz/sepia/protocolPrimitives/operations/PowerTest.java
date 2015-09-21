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


/**
 * Test cases for the Power operation.
 *
 * @author Dilip Many
 *
 */
public class PowerTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#power(int, long[])}.
	 * <p>
	 * Tests if result of MPC modular power is equal to result of normally computed modular power.
	 */
	public void testPower() {
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting power operation with group order=" + fieldSize);

			// do exponentiation
			long[] exponents = {1, 2, 3, 9, 15, 127, 3498, 63226, 9223372036854775782L};
			int[] operationIDs = new int[input.length*exponents.length];
			int nextID = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int exponentIndex = 0; exponentIndex < exponents.length; exponentIndex++) {
						nextID = inputIndex*exponents.length + exponentIndex;
						operationIDs[nextID] = nextID;
						data = new long[2];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = exponents[exponentIndex];
						primitives[privacyPeerIndex].power(nextID, data);
					}
				}
			}
			doOperation(operationIDs);

			// get exponentiation results
			long[][][] exponentiationResults = new long[numberOfPrivacyPeers][input.length][exponents.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int exponentIndex = 0; exponentIndex < exponents.length; exponentIndex++) {
						nextID = inputIndex*exponents.length + exponentIndex;
						exponentiationResults[privacyPeerIndex][inputIndex][exponentIndex] = primitives[privacyPeerIndex].getResult(nextID)[0];
					}
				}
			}

			// reconstruct exponentiation results
			operationIDs = new int[numberOfPeers*input.length*exponents.length];
			nextID = 0;
			data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int exponentIndex = 0; exponentIndex < exponents.length; exponentIndex++) {
							nextID = peerIndex*(input.length*exponents.length) + inputIndex*exponents.length + exponentIndex;
							operationIDs[nextID] = nextID;
							data = new long[1];
							data[0] = exponentiationResults[privacyPeerIndex][inputIndex][exponentIndex];
							primitives[privacyPeerIndex].reconstruct(nextID, data);
						}
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][] computedResults = new long[numberOfPrivacyPeers][input.length][exponents.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int exponentIndex = 0; exponentIndex < exponents.length; exponentIndex++) {
							nextID = peerIndex*(input.length*exponents.length) + inputIndex*exponents.length + exponentIndex;
							computedResults[privacyPeerIndex][inputIndex][exponentIndex] = primitives[privacyPeerIndex].getResult(nextID)[0];
						}
					}
				}
			}

			// assert that results of reconstruction are equal to real result
			long realResult = 0;
			BigInteger bigBase = null;
			BigInteger bigExponent = null;
			BigInteger bigGroupOrder = BigInteger.valueOf(fieldSize);
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int exponentIndex = 0; exponentIndex < exponents.length; exponentIndex++) {
							bigBase = BigInteger.valueOf(input[inputIndex]);
							bigExponent = BigInteger.valueOf(exponents[exponentIndex]);
							realResult = bigBase.modPow(bigExponent, bigGroupOrder).longValue();
							assertEquals("checking exponentiation of "+input[inputIndex]+"^"+exponents[exponentIndex]+" mod "+fieldSize+": ", realResult, computedResults[privacyPeerIndex][inputIndex][exponentIndex]);
						}
					}
				}
			}
		}
	}


	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#power(int, long[])}.
	 * <p>
	 * Tests if result of MPC modular power is equal to result of normally computed modular power.
	 * The power operations are done one after the other, not in parallel.
	 */
	public void testPowerSerial() {
		// (do serial computation)
		long[] exponents = {1, 2, 3, 4, 9, 15, 127, 3498, 63226};
		long realResult = 0;
		BigInteger bigBase = null;
		BigInteger bigExponent = null;
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			BigInteger bigGroupOrder = BigInteger.valueOf(fieldSize);
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting power operation with group order=" + fieldSize);

			// do exponentiation
			int[] operationIDs = new int[1];
			int nextID = 0;
			long[] data = null;
			long[] exponentiationResults = new long[numberOfPrivacyPeers];
			long[] computedResults = new long[numberOfPrivacyPeers];
			for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
				for(int exponentIndex = 0; exponentIndex < exponents.length; exponentIndex++) {
					for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
						primitives[privacyPeerIndex].initialize(operationIDs.length);
						operationIDs[0] = nextID;
						data = new long[2];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = exponents[exponentIndex];
						primitives[privacyPeerIndex].power(nextID, data);
					}
					doOperation(operationIDs);
					for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
						exponentiationResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(nextID)[0];
						primitives[privacyPeerIndex].initialize(operationIDs.length);
						data = new long[1];
						data[0] = exponentiationResults[privacyPeerIndex];
						primitives[privacyPeerIndex].reconstruct(nextID, data);
					}
					doOperation(operationIDs);
					for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
						computedResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(nextID)[0];
						bigBase = BigInteger.valueOf(input[inputIndex]);
						bigExponent = BigInteger.valueOf(exponents[exponentIndex]);
						realResult = bigBase.modPow(bigExponent, bigGroupOrder).longValue();
						assertEquals("checking exponentiation of "+input[inputIndex]+"^"+exponents[exponentIndex]+" mod "+fieldSize+" for PP "+privacyPeerIndex+": ", realResult, computedResults[privacyPeerIndex]);
					}
				}
			}
		}
	}
}
