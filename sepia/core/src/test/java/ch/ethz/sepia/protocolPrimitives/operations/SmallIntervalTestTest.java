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
 * Test cases for SmallIntervalTest operation.
 *
 * @author Dilip Many
 *
 */
public class SmallIntervalTestTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#smallIntervalTest(int, long[])}.
	 * <p>
	 * Tests if the numbers which are supposedly in the interval by the MPC computation are really in the interval (and vice versa).
	 */
	public void testSmallIntervalTest() {
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting small interval test operation with field size=" + fieldSize);

			for(int intervalIndex = 0; intervalIndex < 10; intervalIndex++) {
				// create interval
				long lowerBound = random.nextLong() % fieldSize;
				if(lowerBound < 0) {
					lowerBound = 0-lowerBound;
				}
				long intervalSize = 0;
				if((fieldSize-lowerBound-1) > 0) {
					intervalSize = random.nextLong() % (fieldSize-lowerBound-1);
				}
				if(intervalSize < 0) {
					intervalSize = 0-intervalSize;
				}
				long upperBound = lowerBound + intervalSize;

				// do small interval test operation
				int[] operationIDs = new int[input.length];
				long[] data = null;
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						operationIDs[inputIndex] = inputIndex;
						data = new long[3];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = lowerBound;
						data[2] = upperBound;
						primitives[privacyPeerIndex].smallIntervalTest(inputIndex, data);
					}
				}
				doOperation(operationIDs);

				// get small interval test results
				long[][] smallIntervalTestResults = new long[numberOfPrivacyPeers][input.length];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						smallIntervalTestResults[privacyPeerIndex][inputIndex] = primitives[privacyPeerIndex].getResult(inputIndex)[0];
					}
				}

				// reconstruct small interval test results
				operationIDs = new int[input.length];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						operationIDs[inputIndex] = inputIndex;
						data = new long[1];
						data[0] = smallIntervalTestResults[privacyPeerIndex][inputIndex];
						primitives[privacyPeerIndex].reconstruct(inputIndex, data);
					}
				}
				doOperation(operationIDs);

				// get results
				long[][] computedResults = new long[numberOfPrivacyPeers][input.length];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						computedResults[privacyPeerIndex][inputIndex] = primitives[privacyPeerIndex].getResult(inputIndex)[0];
					}
				}

				// assert that results of reconstruction are equal to original input
				long realResult = 0;
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						if( (input[inputIndex] >= lowerBound) && (input[inputIndex] <= upperBound)) {
							realResult = 1;
						} else {
							realResult = 0;
						}
						assertEquals("checking if "+input[inputIndex]+" is in ["+lowerBound+","+upperBound+"]: ", realResult, computedResults[privacyPeerIndex][inputIndex]);
					}
				}
			}
		}
	}
}
