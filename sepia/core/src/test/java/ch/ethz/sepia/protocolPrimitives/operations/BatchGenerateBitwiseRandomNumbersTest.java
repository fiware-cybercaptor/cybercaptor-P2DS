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
 * Test cases for BatchGenerateBitwiseRandomNumbers operation.
 *
 * @author Dilip Many
 *
 */
public class BatchGenerateBitwiseRandomNumbersTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#batchGenerateBitwiseRandomNumbers(int, long[])}.
	 * <p>
	 * Checks if the operation successfully created the requested amount of bitwise shared numbers and if they are valid field elements.
	 */
	public void testBatchGenerateBitwiseRandomNumbers() {
		int randomNumbersCount = 2*borderSize+randomValuesCount;
		/** the number of sub-operation creations, the number of additional random numbers per fieldSize */
		int[][] statistics = new int[fieldSizes.length][2];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			int bitsCount = primitives[0].getBitsCount();
			System.out.println("\ntesting batch generate bitwise (shared) random number operation with field size=" + fieldSize);

			// batch generate bitwise (shared) random numbers
			int[] operationIDs = new int[1];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
//				primitives[privacyPeerIndex].batchGenerateCallsCount = 0;
				data = new long[1];
				data[0] = randomNumbersCount;
				operationIDs[0] = 0;
				primitives[privacyPeerIndex].batchGenerateBitwiseRandomNumbers(0, data);
			}
			doOperation(operationIDs);


			// check amount of generated bits
			int bitwiseRandomNumbersCount = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				bitwiseRandomNumbersCount = primitives[privacyPeerIndex].getResult(0).length / bitsCount;
				assertTrue("insufficient amount of bits generated", bitwiseRandomNumbersCount >= randomNumbersCount);
				assertTrue("amount of bits generated ("+primitives[privacyPeerIndex].getResult(0).length+") is weird", (primitives[privacyPeerIndex].getResult(0).length % bitsCount) == 0);
			}


			// get random number bit shares
			long[][][] bitwiseRandomNumberShares = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				// store results
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					System.arraycopy(primitives[privacyPeerIndex].getResult(0), randomNumberIndex*bitsCount, bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex], 0, bitsCount);
					bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex] = primitives[privacyPeerIndex].getResult(0);
				}
			}

			// reconstruct random bits and random numbers
			operationIDs = new int[bitwiseRandomNumbersCount*(bitsCount+1)];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				// reconstruct random numbers
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					operationIDs[randomNumberIndex] = randomNumberIndex;
					data = new long[1];
					data[0] = primitives[privacyPeerIndex].computeNumber( bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex] );
					primitives[privacyPeerIndex].reconstruct(randomNumberIndex, data);
				}
				// reconstruct bit shares of random numbers
				int index = 0;
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					for(int bitIndex = 0; bitIndex < bitsCount; bitIndex++) {
						index = bitwiseRandomNumbersCount+ randomNumberIndex*bitsCount + bitIndex;
						operationIDs[index] = index;
						data = new long[1];
						data[0] = bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex][bitIndex];
						primitives[privacyPeerIndex].reconstruct(index, data);
					}
				}
			}
			doOperation(operationIDs);

			// get random numbers and bits
			long[][] computedNumber = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					computedNumber[privacyPeerIndex][randomNumberIndex] = primitives[privacyPeerIndex].getResult(randomNumberIndex)[0];
				}
			}
			long[][][] computedBits = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				int index = 0;
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					for(int bitIndex = 0; bitIndex < bitsCount; bitIndex++) {
						index = bitwiseRandomNumbersCount+ randomNumberIndex*bitsCount + bitIndex;
						computedBits[privacyPeerIndex][randomNumberIndex][bitIndex] = primitives[privacyPeerIndex].getResult(index)[0];
					}
				}
			}

			// assert that random numbers represent valid field elements
			boolean valid = false;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					valid = (0 <= computedNumber[privacyPeerIndex][randomNumberIndex]) && (computedNumber[privacyPeerIndex][randomNumberIndex] < fieldSize);
					assertTrue("checking validity of random number "+computedNumber[privacyPeerIndex][randomNumberIndex]+" failed (in field of size "+fieldSize+")", valid);
				}
			}
			// assert that the bits match the numbers
			long computedResult = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex = 0; randomNumberIndex < bitwiseRandomNumbersCount; randomNumberIndex++) {
					computedResult = primitives[0].computeNumber( computedBits[privacyPeerIndex][randomNumberIndex]);
					assertEquals("checking validity of bit-shares of random number "+computedNumber[privacyPeerIndex][randomNumberIndex]+" failed (in field of size "+fieldSize+")", computedNumber[privacyPeerIndex][randomNumberIndex], computedResult);
				}
			}

//			statistics[fieldSizeIndex][0] = primitives[0].batchGenerateCallsCount;
			statistics[fieldSizeIndex][1] = bitwiseRandomNumbersCount - randomNumbersCount;
			System.out.println("number of batch generate sub-operation creation calls (in field of size="+fieldSize+"): "+statistics[fieldSizeIndex]);
		}
		System.out.println("\nnumber of batch generate sub-operation creation calls (field size, sub-operation creations, additionally created numbers):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+statistics[i][0]+"\t"+statistics[i][1]);
		}
	}
}
