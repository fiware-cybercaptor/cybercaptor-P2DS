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
 * Test cases for GenerateRandomBit operation.
 *
 * @author Dilip Many
 *
 */
public class GenerateRandomBitTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#generateRandomBit(int, long[])}.
	 * <p>
	 * Tests if the generated random bit is a valid bit (0 or 1) or the operation failed.
	 */
	public void testGenerateRandomBit() {
		int randomBitsCount = 2*borderSize+randomValuesCount;
		int[] failuresCount = new int[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			System.out.println("\ntesting generate random bit operation with field size=" + fieldSize);

			// generate random bits
			int[] operationIDs = new int[randomBitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int randomBitIndex = 0; randomBitIndex < randomBitsCount; randomBitIndex++) {
					operationIDs[randomBitIndex] = randomBitIndex;
					primitives[privacyPeerIndex].generateRandomBit(randomBitIndex, null);
				}
			}
			doOperation(operationIDs);

			// get random bit shares
			long[][] randomBitShares = new long[numberOfPrivacyPeers][randomBitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomBitIndex = 0; randomBitIndex < randomBitsCount; randomBitIndex++) {
					randomBitShares[privacyPeerIndex][randomBitIndex] = primitives[privacyPeerIndex].getResult(randomBitIndex)[0];
				}
			}

			// reconstruct random bits
			operationIDs = new int[randomBitsCount];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int randomBitIndex = 0; randomBitIndex < randomBitsCount; randomBitIndex++) {
					operationIDs[randomBitIndex] = randomBitIndex;
					data = new long[1];
					data[0] = randomBitShares[privacyPeerIndex][randomBitIndex];
					primitives[privacyPeerIndex].reconstruct(randomBitIndex, data);
				}
			}
			doOperation(operationIDs);

			// get random bits
			long[][] computedResults = new long[numberOfPrivacyPeers][randomBitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomBitIndex = 0; randomBitIndex < randomBitsCount; randomBitIndex++) {
					computedResults[privacyPeerIndex][randomBitIndex] = primitives[privacyPeerIndex].getResult(randomBitIndex)[0];
				}
			}

			// assert that random bits are valid bits
			boolean valid = false;
			int failures = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomBitIndex = 0; randomBitIndex < randomBitsCount; randomBitIndex++) {
					if(computedResults[privacyPeerIndex][randomBitIndex] != -1L) {
						valid = (0 == computedResults[privacyPeerIndex][randomBitIndex]) || (1 == computedResults[privacyPeerIndex][randomBitIndex]);
						assertTrue("checking validity of random bit "+computedResults[privacyPeerIndex][randomBitIndex]+" failed (in field of size "+fieldSize+")", valid);
					} else {
						failures++;
					}
				}
			}
			failuresCount[fieldSizeIndex] = failures;
			System.out.println("generation of random bits (in field of size="+fieldSize+") failed in "+failures+" of "+randomBitsCount+" tries");
		}
		System.out.println("generation of random bits failure rates (field size, failures) for "+randomBitsCount+" tries:");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failuresCount[i]);
		}
	}
}
