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
 * Test cases for GenerateBitwiseRandomNumber operation.
 *
 * @author Dilip Many
 *
 */
public class GenerateBitwiseRandomNumberTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#generateBitwiseRandomNumber(int, long[])}.
	 * <p>
	 * Tests if the generated bitwise shared random number is a valid bit field element or the operation failed.
	 */
	public void testGenerateBitwiseRandomNumber() {
		boolean usePreGeneratedRandomBits = true;
		int randomNumbersCount = 2*borderSize+randomValuesCount;
		int[] failuresCount = new int[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			int bitsCount = primitives[0].getBitsCount();
			System.out.println("\ntesting generate bitwise (shared) random number operation with field size=" + fieldSize);

			int[] operationIDs = null;

			// generate bitwise (shared) random numbers (without bit shares argument)
			if(!usePreGeneratedRandomBits) {
				operationIDs = new int[randomNumbersCount];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
						operationIDs[randomNumberIndex] = randomNumberIndex;
						primitives[privacyPeerIndex].generateBitwiseRandomNumber(randomNumberIndex, null);
					}
				}
				doOperation(operationIDs);
			}

			// test with pre-generated bits
			if(usePreGeneratedRandomBits) {
				// generate bits
				operationIDs = new int[randomNumbersCount*bitsCount*3];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int randomBitIndex = 0; randomBitIndex < operationIDs.length; randomBitIndex++) {
						operationIDs[randomBitIndex] = randomBitIndex;
						primitives[privacyPeerIndex].generateRandomBit(randomBitIndex, null);
					}
				}
				doOperation(operationIDs);

				// get successfully generated bits
				long[][] randomBits = new long[numberOfPrivacyPeers][operationIDs.length];
				int nextBit = 0;
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					nextBit = 0;
					for(int randomBitIndex = 0; randomBitIndex < operationIDs.length; randomBitIndex++) {
						if(primitives[privacyPeerIndex].getResult(randomBitIndex)[0] != -1L) {
							randomBits[privacyPeerIndex][nextBit] = primitives[privacyPeerIndex].getResult(randomBitIndex)[0];
							nextBit++;
						}
					}
				}
				int randomBitsCount = nextBit;

				// generate bitwise (shared) random numbers with the pre-generated bits
				operationIDs = new int[randomBitsCount/bitsCount];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					nextBit = 0;
					for(int randomNumberIndex = 0; randomNumberIndex < operationIDs.length; randomNumberIndex++) {
						operationIDs[randomNumberIndex] = randomNumberIndex;
						long[] data = new long[bitsCount];
						System.arraycopy(randomBits[privacyPeerIndex], nextBit, data, 0, bitsCount);
						nextBit += bitsCount;
						primitives[privacyPeerIndex].generateBitwiseRandomNumber(randomNumberIndex, data);
					}
				}
				doOperation(operationIDs);
			}

			// count number of successfully created random numbers
			int bitwiseRandomNumbersCount = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				bitwiseRandomNumbersCount = 0;
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					if(primitives[privacyPeerIndex].getResult(randomNumberIndex)[0] != -1) {
						bitwiseRandomNumbersCount++;
					}
				}
			}

			// get random number bit shares
			long[][][] bitwiseRandomNumberShares = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				int nextSlot = 0;
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					if(primitives[privacyPeerIndex].getResult(randomNumberIndex)[0] != -1) {
						bitwiseRandomNumberShares[privacyPeerIndex][nextSlot] = primitives[privacyPeerIndex].getResult(randomNumberIndex);
						nextSlot++;
					}
				}
			}

			// reconstruct random bits and random numbers
			operationIDs = new int[bitwiseRandomNumbersCount*(bitsCount+1)];
			long[] data = null;
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

			failuresCount[fieldSizeIndex] = randomNumbersCount - bitwiseRandomNumbersCount;
			System.out.println("generation of random bits (in field of size="+fieldSize+") failed in "+failuresCount[fieldSizeIndex]+" of "+randomNumbersCount+" tries");
		}
		System.out.println("\ngeneration of random bits failure rates (field size, failures) for "+randomNumbersCount+" tries:");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failuresCount[i]);
		}
	}


	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#generateBitwiseRandomNumber(int, long[])}.
	 * <p>
	 * Generates bitwise shared random numbers adds them together and multiplies them using MPC operations and checks if the results are right.
	 */
	public void testGenerateBitwiseRandomNumber2() {
		boolean usePreGeneratedRandomBits = true;
		int randomNumbersCount = 2*borderSize+randomValuesCount;
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			int bitsCount = primitives[0].getBitsCount();
			System.out.println("\ntesting generate bitwise (shared) random number operation with field size=" + fieldSize);

			int[] operationIDs = null;

			// generate bitwise (shared) random numbers (without bit shares argument)
			if(!usePreGeneratedRandomBits) {
				operationIDs = new int[randomNumbersCount];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
						operationIDs[randomNumberIndex] = randomNumberIndex;
						primitives[privacyPeerIndex].generateBitwiseRandomNumber(randomNumberIndex, null);
					}
				}
				doOperation(operationIDs);
			}

			// test with pre-generated bits
			if(usePreGeneratedRandomBits) {
				// generate bits
				operationIDs = new int[randomNumbersCount*bitsCount];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					for(int randomBitIndex = 0; randomBitIndex < operationIDs.length; randomBitIndex++) {
						operationIDs[randomBitIndex] = randomBitIndex;
						primitives[privacyPeerIndex].generateRandomBit(randomBitIndex, null);
					}
				}
				doOperation(operationIDs);

				// get successfully generated bits
				long[][] randomBits = new long[numberOfPrivacyPeers][operationIDs.length];
				int nextBit = 0;
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					nextBit = 0;
					for(int randomBitIndex = 0; randomBitIndex < operationIDs.length; randomBitIndex++) {
						if(primitives[privacyPeerIndex].getResult(randomBitIndex)[0] != -1L) {
							randomBits[privacyPeerIndex][nextBit] = primitives[privacyPeerIndex].getResult(randomBitIndex)[0];
							nextBit++;
						}
					}
				}
				int randomBitsCount = nextBit;

				// generate bitwise (shared) random numbers with the pre-generated bits
				operationIDs = new int[randomBitsCount/bitsCount];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					primitives[privacyPeerIndex].initialize(operationIDs.length);
					nextBit = 0;
					for(int randomNumberIndex = 0; randomNumberIndex < operationIDs.length; randomNumberIndex++) {
						operationIDs[randomNumberIndex] = randomNumberIndex;
						long[] data = new long[bitsCount];
						System.arraycopy(randomBits[privacyPeerIndex], nextBit, data, 0, bitsCount);
						nextBit += bitsCount;
						primitives[privacyPeerIndex].generateBitwiseRandomNumber(randomNumberIndex, data);
					}
				}
				doOperation(operationIDs);
			}
			randomNumbersCount = operationIDs.length;

			// count number of successfully created random numbers
			int bitwiseRandomNumbersCount = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				bitwiseRandomNumbersCount = 0;
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					if(primitives[privacyPeerIndex].getResult(randomNumberIndex)[0] != -1) {
						bitwiseRandomNumbersCount++;
					}
				}
			}

			// get random number bit shares
			long[][][] bitwiseRandomNumberShares = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				int nextSlot = 0;
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					if(primitives[privacyPeerIndex].getResult(randomNumberIndex)[0] != -1) {
						bitwiseRandomNumberShares[privacyPeerIndex][nextSlot] = primitives[privacyPeerIndex].getResult(randomNumberIndex);
						nextSlot++;
					}
				}
			}

			// add numbers
			long[][][] addedNumbers = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitwiseRandomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						addedNumbers[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2] = mpcShamirSharingPeers[0].modAdd(primitives[privacyPeerIndex].computeNumber(bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex1]), primitives[privacyPeerIndex].computeNumber(bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex2]));
					}
				}
			}

			// reconstruct added numbers
			operationIDs = new int[bitwiseRandomNumbersCount*bitwiseRandomNumbersCount];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				int index = 0;
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						index = randomNumberIndex1*bitwiseRandomNumbersCount + randomNumberIndex2;
						operationIDs[index] = index;
						data = new long[1];
						data[0] = addedNumbers[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2];
						primitives[privacyPeerIndex].reconstruct(index, data);
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][] addedNumbersResults = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitwiseRandomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						addedNumbersResults[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2] = primitives[privacyPeerIndex].getResult(randomNumberIndex1*bitwiseRandomNumbersCount + randomNumberIndex2)[0];
					}
				}
			}

			// multiply numbers
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				int index = 0;
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						index = randomNumberIndex1*bitwiseRandomNumbersCount + randomNumberIndex2;
						operationIDs[index] = index;
						data = new long[2];
						data[0] = primitives[privacyPeerIndex].computeNumber(bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex1]);
						data[1] = primitives[privacyPeerIndex].computeNumber(bitwiseRandomNumberShares[privacyPeerIndex][randomNumberIndex2]);
						primitives[privacyPeerIndex].multiply(index, data);
					}
				}
			}
			doOperation(operationIDs);

			// get multiplied number shares
			long[][][] multipliedNumberShares = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitwiseRandomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						multipliedNumberShares[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2] = primitives[privacyPeerIndex].getResult(randomNumberIndex1*bitwiseRandomNumbersCount + randomNumberIndex2)[0];
					}
				}
			}

			// reconstruct multiplied numbers
			operationIDs = new int[bitwiseRandomNumbersCount*bitwiseRandomNumbersCount];
			data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				int index = 0;
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						index = randomNumberIndex1*bitwiseRandomNumbersCount + randomNumberIndex2;
						operationIDs[index] = index;
						data = new long[1];
						data[0] = multipliedNumberShares[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2];
						primitives[privacyPeerIndex].reconstruct(index, data);
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][] multipliedNumbersResults = new long[numberOfPrivacyPeers][bitwiseRandomNumbersCount][bitwiseRandomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						multipliedNumbersResults[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2] = primitives[privacyPeerIndex].getResult(randomNumberIndex1*bitwiseRandomNumbersCount + randomNumberIndex2)[0];
					}
				}
			}

			// reconstruct random bits and random numbers
			operationIDs = new int[bitwiseRandomNumbersCount*(bitsCount+1)];
			data = null;
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

			// assert that the added numbers are right
			long realResult = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						realResult = mpcShamirSharingPeers[0].modAdd(computedNumber[privacyPeerIndex][randomNumberIndex1], computedNumber[privacyPeerIndex][randomNumberIndex2]);
						assertEquals("adding numbers failed: "+computedNumber[privacyPeerIndex][randomNumberIndex1]+"+"+computedNumber[privacyPeerIndex][randomNumberIndex2]+": ", realResult, addedNumbersResults[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2]);
					}
				}
			}

			// assert that the added numbers are right
			realResult = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex1 = 0; randomNumberIndex1 < bitwiseRandomNumbersCount; randomNumberIndex1++) {
					for(int randomNumberIndex2 = 0; randomNumberIndex2 < bitwiseRandomNumbersCount; randomNumberIndex2++) {
						realResult = mpcShamirSharingPeers[0].modMultiply(computedNumber[privacyPeerIndex][randomNumberIndex1], computedNumber[privacyPeerIndex][randomNumberIndex2]);
						assertEquals("multiplying numbers failed: "+computedNumber[privacyPeerIndex][randomNumberIndex1]+"*"+computedNumber[privacyPeerIndex][randomNumberIndex2]+": ", realResult, multipliedNumbersResults[privacyPeerIndex][randomNumberIndex1][randomNumberIndex2]);
					}
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
		}
	}
}
