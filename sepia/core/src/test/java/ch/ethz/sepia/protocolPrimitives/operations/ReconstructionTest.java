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

import java.util.Random;


/**
 * Test cases for the Reconstruction operation.
 *
 * @author Dilip Many
 *
 */
public class ReconstructionTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#reconstruct(int, long[])}.
	 * <p>
	 * Test if reconstructed value is equal to originally shared value.
	 */
	public void testReconstruct() {
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();

			System.out.println("\ntesting reconstruction operation with group order=" + fieldSize);

			// reconstruct input from the shares
			int[] operationIDs = new int[numberOfPeers*input.length];
			int nextID = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						nextID = peerIndex*input.length + inputIndex;
						operationIDs[nextID] = nextID;
						data = new long[1];
						data[0] = inputShares[peerIndex][privacyPeerIndex][inputIndex];
						primitives[privacyPeerIndex].reconstruct(nextID, data);
					}
				}
			}

			doOperation(operationIDs);

			// get results
			int resultIndex = 0;
			long[][] computedResults = new long[numberOfPrivacyPeers][numberOfPeers*input.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						resultIndex = peerIndex*input.length + inputIndex;
						computedResults[privacyPeerIndex][resultIndex] = primitives[privacyPeerIndex].getResult(resultIndex)[0];
					}
				}
			}

			// assert that results of reconstruction are equal to original input
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						resultIndex = peerIndex*input.length + inputIndex;
						assertEquals("checking equality for computedResults["+privacyPeerIndex+"]["+resultIndex+"]: ", input[inputIndex], computedResults[privacyPeerIndex][resultIndex]);
					}
				}
			}
		}
	}
	
	/**
	 * Similar to {@link #testReconstruct()}, but simulates the loss of messages.
	 */
	public void testLossyReconstruct() {
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();

			System.out.println("\ntesting reconstruction operation with group order=" + fieldSize);

			// reconstruct input from the shares
			int[] operationIDs = new int[numberOfPeers*input.length];
			int nextID = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						nextID = peerIndex*input.length + inputIndex;
						operationIDs[nextID] = nextID;
						data = new long[1];
						data[0] = inputShares[peerIndex][privacyPeerIndex][inputIndex];
						primitives[privacyPeerIndex].reconstruct(nextID, data);
					}
				}
			}

			// Now pick the failing PP
			Random rand = new Random();
			int failingPP = rand.nextInt(numberOfPrivacyPeers);
			doLossyOperation(operationIDs, failingPP, false);

			// get results
			int resultIndex = 0;
			long[][] computedResults = new long[numberOfPrivacyPeers][numberOfPeers*input.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						resultIndex = peerIndex*input.length + inputIndex;
						computedResults[privacyPeerIndex][resultIndex] = primitives[privacyPeerIndex].getResult(resultIndex)[0];
					}
				}
			}

			// assert that results of reconstruction are equal to original input
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						resultIndex = peerIndex*input.length + inputIndex;
						assertEquals("checking equality for computedResults["+privacyPeerIndex+"]["+resultIndex+"]: ", input[inputIndex], computedResults[privacyPeerIndex][resultIndex]);
					}
				}
			}
		}
	}

}
