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
 * Test cases for the Min operation.
 *
 * @author Manuel Widmer, ETH Zurich
 *
 */
public class MinTest extends OperationsTest {
	
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#min(int, long[])}.
	 * <p>
	 * Tests if result of MPC minimum is equal to result of normal minimum.
	 */
	public void testMinFewRounds(){
		
		numberOfPeers = 5;
		
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting min operation with group order=" + fieldSize);
			
			
			// compute min of inputs
			int[] operationIDs = new int[numberOfPeers];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					operationIDs[peerIndex] = peerIndex;
					primitives[privacyPeerIndex].min(peerIndex, inputShares[peerIndex][privacyPeerIndex], -1, true);
				}
			}
			
			doOperation(operationIDs);
			
			// get minimum results
			long[][] minResults = new long[numberOfPrivacyPeers][numberOfPeers];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					minResults[privacyPeerIndex][peerIndex] = primitives[privacyPeerIndex].getResult(peerIndex)[0];
				}
			}
			
			// reconstruct minimum results
			operationIDs = new int[operationIDs.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					operationIDs[peerIndex] = peerIndex;
					data = new long[1];
					data[0] = minResults[privacyPeerIndex][peerIndex];
					primitives[privacyPeerIndex].reconstruct(peerIndex, data);
				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][] computedResults = new long[numberOfPrivacyPeers][numberOfPeers];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					computedResults[privacyPeerIndex][peerIndex] = primitives[privacyPeerIndex].getResult(peerIndex)[0];
				}
			}

			// assert that results of reconstruction are equal to original input
			long realResult = computeInputMinimum();
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					assertEquals("checking minimum of peer "+peerIndex+" in field of size "+fieldSize+": ", realResult, computedResults[privacyPeerIndex][peerIndex]);
				}
			}
		}
	}
	
	public void testMinManyRounds(){
		
		numberOfPeers = 5;
		
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting min operation with group order=" + fieldSize);
			
			
			// compute min of inputs
			int[] operationIDs = new int[numberOfPeers];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					operationIDs[peerIndex] = peerIndex;
					primitives[privacyPeerIndex].min(peerIndex, inputShares[peerIndex][privacyPeerIndex], -1, false);
				}
			}
			
			doOperation(operationIDs);
			
			// get minimum results
			long[][] minResults = new long[numberOfPrivacyPeers][numberOfPeers];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					minResults[privacyPeerIndex][peerIndex] = primitives[privacyPeerIndex].getResult(peerIndex)[0];
				}
			}
			
			// reconstruct minimum results
			operationIDs = new int[operationIDs.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					operationIDs[peerIndex] = peerIndex;
					data = new long[1];
					data[0] = minResults[privacyPeerIndex][peerIndex];
					primitives[privacyPeerIndex].reconstruct(peerIndex, data);
				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][] computedResults = new long[numberOfPrivacyPeers][numberOfPeers];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					computedResults[privacyPeerIndex][peerIndex] = primitives[privacyPeerIndex].getResult(peerIndex)[0];
				}
			}

			// assert that results of reconstruction are equal to original input
			long realResult = computeInputMinimum();
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					assertEquals("checking minimum of peer "+peerIndex+" in field of size "+fieldSize+": ", realResult, computedResults[privacyPeerIndex][peerIndex]);
				}
			}
		}
	}
	
	public long computeInputMinimum(){
		long min = input[0];
		for(int i = 1; i < input.length; i++) {
			if(input[i]<min){
				min = input[i];
			}
		}
		return min;
	}

}
