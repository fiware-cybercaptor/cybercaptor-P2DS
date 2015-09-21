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
 * Test cases for LinearPrefixOr operation.
 *
 * @author Dilip Many
 *
 */
public class LinearPrefixOrTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#linearPrefixOr(int, long[])}.
	 * <p>
	 * Tests if the computed prefix-or is correct.
	 */
	public void testLinearPrefixOr() {
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputBitShares();
			int bitsCount = primitives[0].getBitsCount();
			System.out.println("\ntesting linear prefix-or operation with field size=" + fieldSize);

			// compute prefix-or
			int[] operationIDs = new int[input.length];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					operationIDs[inputIndex] = inputIndex;
					data = new long[bitsCount];
					System.arraycopy(inputBitShares[0][privacyPeerIndex][inputIndex], 0, data, 0, inputBitShares[0][privacyPeerIndex][inputIndex].length);
					primitives[privacyPeerIndex].linearPrefixOr(inputIndex, data);
				}
			}
			doOperation(operationIDs);

			// get prefix-or results
			long[][][] prefixOrResults = new long[numberOfPrivacyPeers][input.length][bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					data = new long[bitsCount];
					System.arraycopy(primitives[privacyPeerIndex].getResult(inputIndex), 0, data, 0, primitives[privacyPeerIndex].getResult(inputIndex).length);
					prefixOrResults[privacyPeerIndex][inputIndex] = data;
				}
			}

			// reconstruct prefix-or results
			operationIDs = new int[input.length*bitsCount];
			int index = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int bitIndex = 0; bitIndex < bitsCount; bitIndex++) {
						index = inputIndex*bitsCount+bitIndex;
						operationIDs[index] = index;
						data = new long[1];
						data[0] = prefixOrResults[privacyPeerIndex][inputIndex][bitIndex];
						primitives[privacyPeerIndex].reconstruct(index, data);
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][] computedResults = new long[numberOfPrivacyPeers][input.length][bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int bitIndex = 0; bitIndex < bitsCount; bitIndex++) {
						index = inputIndex*bitsCount+bitIndex;
						computedResults[privacyPeerIndex][inputIndex][bitIndex] = primitives[privacyPeerIndex].getResult(index)[0];
					}
				}
			}

			// check result
			long[] realResult = new long[bitsCount];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					realResult = prefixOr(input[inputIndex]);
					for(int bitIndex = 0; bitIndex < bitsCount; bitIndex++) {
						assertEquals("checking prefix-or bit of pp="+privacyPeerIndex+", input="+input[inputIndex]+", bitIndex="+bitIndex+" (in field of size="+fieldSize+"): ", realResult[bitIndex], computedResults[privacyPeerIndex][inputIndex][bitIndex]);
					}
				}
			}
		}
	}


	/**
	 * computes the prefix-or of the specified value
	 *
	 * @param value		the value to compute the prefix-or of
	 * @return			the bits of the prefix-or
	 */
	private long[] prefixOr(long value) {
		int bitsCount = primitives[0].getBitsCount();
		long[] result = new long[bitsCount];
		// get bits of input
		String inputString = Long.toBinaryString(value);
		// create leading zeroes
		int bitIndex = 0;
		if(inputString.length() < bitsCount) {
			for(int j = inputString.length(); j < bitsCount; j++) {
				result[bitIndex] = 0;
				bitIndex++;
			}
		}
		// compute prefix-or
		long left = 0;
		for(int j = 0; j < inputString.length(); j++) {
			if(inputString.charAt(j) == '0') {
				result[bitIndex] = left;
			} else {
				left = 1;
				result[bitIndex] = left;
			}
			bitIndex++;
		}
		return result;
	}
}
