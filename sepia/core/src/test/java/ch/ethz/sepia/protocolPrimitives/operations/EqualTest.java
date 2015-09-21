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


/**
 * Test cases for Equal operation.
 *
 * @author Dilip Many
 *
 */
public class EqualTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#equal(int, long[])}.
	 * <p>
	 * Tests if numbers that are supposedly equal by MPC computation are really equal (and vice versa).
	 */
	public void testEqual() {
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting equal operation with field size=" + fieldSize);

			// test inputs for equality
			int[] operationIDs = new int[numberOfPeers*input.length*input.length];
			int nextID = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = peerIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[2];
							data[0] = inputShares[peerIndex][privacyPeerIndex][inputIndex];
							data[1] = inputShares[peerIndex][privacyPeerIndex][inputIndex2];
							primitives[privacyPeerIndex].equal(nextID, data);
						}
					}
				}
			}
			doOperation(operationIDs);

			// get equality test results
			long[][][] equalityResults = new long[numberOfPrivacyPeers][input.length][input.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = peerIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							equalityResults[privacyPeerIndex][inputIndex][inputIndex2] = primitives[privacyPeerIndex].getResult(nextID)[0];
						}
					}
				}
			}

			// reconstruct equality test results
			operationIDs = new int[numberOfPeers*input.length*input.length];
			nextID = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = peerIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[1];
							data[0] = equalityResults[privacyPeerIndex][inputIndex][inputIndex2];
							primitives[privacyPeerIndex].reconstruct(nextID, data);
						}
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][] computedResults = new long[numberOfPrivacyPeers][input.length][input.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = peerIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							computedResults[privacyPeerIndex][inputIndex][inputIndex2] = primitives[privacyPeerIndex].getResult(nextID)[0];
						}
					}
				}
			}

			// assert that results of reconstruction are equal to original input
			long realResult = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							if(input[inputIndex]==input[inputIndex2]) {
								realResult = 1;
							} else {
								realResult = 0;
							}
							assertEquals("checking equality of "+input[inputIndex]+","+input[inputIndex2]+" (in field of size "+fieldSize+"): ", realResult, computedResults[privacyPeerIndex][inputIndex][inputIndex2]);
						}
					}
				}
			}
		}
	}
}
