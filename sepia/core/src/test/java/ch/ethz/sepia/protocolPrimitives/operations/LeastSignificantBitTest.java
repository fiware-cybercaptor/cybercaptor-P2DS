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
 * Test cases for LeastSignificantBit operation.
 *
 * @author Dilip Many
 *
 */
public class LeastSignificantBitTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#leastSignificantBit(int, long[])}.
	 * <p>
	 * Computes the LSB using the MPC operation and then tests it against the real LSB of the original number.
	 */
	public void testLeastSignificantBit() {
		boolean usePreGeneratedRandomNumbers = true;
		int[] failuresCount = new int[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			int bitsCount = primitives[0].getBitsCount();
			System.out.println("\ntesting least significant bit operation with field size=" + fieldSize);

			int[] operationIDs = null;
			long[] data = null;

			if(!usePreGeneratedRandomNumbers) {
				// compute least significant bits of inputs
				operationIDs = new int[input.length];
				data = null;
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						operationIDs[inputIndex] = inputIndex;
						data = new long[1];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						primitives[privacyPeerIndex].leastSignificantBit(inputIndex, data);
					}
				}
				doOperation(operationIDs);
			}

			if(usePreGeneratedRandomNumbers) {
				// batch generate bitwise (shared) random numbers
				operationIDs = new int[1];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					data = new long[1];
					data[0] = numberOfPeers*input.length;
					operationIDs[0] = 0;
					primitives[privacyPeerIndex].batchGenerateBitwiseRandomNumbers(0, data);
				}
				doOperation(operationIDs);

				// retrieve random numbers
				long[][] randomNumbers = new long[numberOfPrivacyPeers][];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					randomNumbers[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
				}

				// compute least significant bits of inputs
				operationIDs = new int[input.length];
				data = null;
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					int nextBit = 0;
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						operationIDs[inputIndex] = inputIndex;
						data = new long[1+bitsCount];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						// test with pre-computed bitwise shared random number
						System.arraycopy(randomNumbers[privacyPeerIndex], nextBit, data, 1, bitsCount);
						nextBit += bitsCount;
						primitives[privacyPeerIndex].leastSignificantBit(inputIndex, data);
//						primitives[privacyPeerIndex].setOperationDescription(inputIndex, "LSB of "+input[inputIndex]);
					}
				}
				doOperation(operationIDs);
			}

//			writeLogs(operationIDs);

			// get least significant bit results (and count number of successful LSB computations)
			long[][] lsbResults = new long[numberOfPrivacyPeers][input.length];
			int lsbCount = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				lsbCount = 0;
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					lsbResults[privacyPeerIndex][inputIndex] = primitives[privacyPeerIndex].getResult(inputIndex)[0];
					if(lsbResults[privacyPeerIndex][inputIndex] != -1L) {
						lsbCount++;
					}
				}
			}

			// reconstruct least significant bit results
			operationIDs = new int[lsbCount];
			int nextId = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				nextId = 0;
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					if(lsbResults[privacyPeerIndex][inputIndex] != -1L) {
						operationIDs[nextId] = nextId;
						data = new long[1];
						data[0] = lsbResults[privacyPeerIndex][inputIndex];
						primitives[privacyPeerIndex].reconstruct(nextId, data);
						nextId++;
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][] computedResults = new long[numberOfPrivacyPeers][lsbCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < lsbCount; inputIndex++) {
					computedResults[privacyPeerIndex][inputIndex] = primitives[privacyPeerIndex].getResult(inputIndex)[0];
				}
			}

			// assert that results of reconstruction are equal to original input
			String inputString = null;
			long realResult = 0;
			int failures = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				nextId = 0;
				failures = 0;
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					if(lsbResults[privacyPeerIndex][inputIndex] != -1L) {
						inputString = Long.toBinaryString(input[inputIndex]);
						realResult = Long.valueOf( inputString.substring(inputString.length()-1, inputString.length()) );
						assertEquals("checking LSB of "+input[inputIndex]+" (in field of size "+fieldSize+"): ", realResult, computedResults[privacyPeerIndex][nextId]);
						nextId++;
					} else {
						failures++;
					}
				}
			}

			failuresCount[fieldSizeIndex] = failures;
			System.out.println("LSB (in field of size="+fieldSize+") failed in "+failuresCount[fieldSizeIndex]+" of "+input.length+" tries");
		}
		System.out.println("\nnumber of failures when computing LSB (fieldSize, failuresCount):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failuresCount[i]);
		}
	}
}
