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
 * Test case for GenerateRandomNumber operation.
 *
 * @author Dilip Many
 *
 */
public class GenerateRandomNumberTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#generateRandomNumber(int, long[])}.
	 * <p>
	 * Tests if the generated random number is a valid field element.
	 */
	public void testGenerateRandomNumber() {
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			System.out.println("\ntesting generate random number operation with field size=" + fieldSize);

			// generate random numbers
			int randomNumbersCount = 2*borderSize+randomValuesCount;
			int[] operationIDs = new int[randomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					operationIDs[randomNumberIndex] = randomNumberIndex;
					primitives[privacyPeerIndex].generateRandomNumber(randomNumberIndex, null);
				}
			}
			doOperation(operationIDs);

			// get random number shares
			long[][] randomNumberShares = new long[numberOfPrivacyPeers][randomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					randomNumberShares[privacyPeerIndex][randomNumberIndex] = primitives[privacyPeerIndex].getResult(randomNumberIndex)[0];
				}
			}

			// reconstruct random numbers
			operationIDs = new int[randomNumbersCount];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					operationIDs[randomNumberIndex] = randomNumberIndex;
					data = new long[1];
					data[0] = randomNumberShares[privacyPeerIndex][randomNumberIndex];
					primitives[privacyPeerIndex].reconstruct(randomNumberIndex, data);
				}
			}
			doOperation(operationIDs);

			// get random numbers
			long[][] computedResults = new long[numberOfPrivacyPeers][randomNumbersCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
					computedResults[privacyPeerIndex][randomNumberIndex] = primitives[privacyPeerIndex].getResult(randomNumberIndex)[0];
				}
			}

			// assert that random numbers are valid field elements and that all privacy peers reconstructed the same one
			boolean valid = false;
			long number = 0;
			for(int randomNumberIndex = 0; randomNumberIndex < randomNumbersCount; randomNumberIndex++) {
				number = computedResults[0][randomNumberIndex];
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					valid = (0 <= computedResults[privacyPeerIndex][randomNumberIndex]) && (computedResults[privacyPeerIndex][randomNumberIndex] < fieldSize);
					assertTrue("checking validity of random number "+computedResults[privacyPeerIndex][randomNumberIndex]+" failed (in field of size "+fieldSize+")", valid);
					assertTrue("checking consistency of random number "+computedResults[privacyPeerIndex][randomNumberIndex]+" failed (privacy peer "+privacyPeerIndex+" in field of size "+fieldSize+")", number == computedResults[privacyPeerIndex][randomNumberIndex]);
				}
			}
		}
	}
}
