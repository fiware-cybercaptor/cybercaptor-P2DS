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
 * Test cases for BitwiseLessThan operation.
 *
 * @author Dilip Many
 *
 */
public class BitwiseLessThanTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#bitwiseLessThan(int, long[])}.
	 * <p>
	 * Tests if the computed less-than is correct.
	 */
	public void testBitwiseLessThan() {
		// compute less than for the 3 different types in parallel
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputBitShares();
			int bitsCount = primitives[0].getBitsCount();
			System.out.println("\ntesting bitwise less-than operation with field size=" + fieldSize);

			int typesCount = 3;
			int[] operationIDs = new int[typesCount*input.length*input.length];
			// compute bitwise less than with 2 bit shared values
			int nextID = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = inputIndex*input.length + inputIndex2;
						operationIDs[nextID] = nextID;
						data = new long[1+2*bitsCount];
						data[0] = 0;
						System.arraycopy(inputBitShares[0][privacyPeerIndex][inputIndex], 0, data, 1, bitsCount);
						System.arraycopy(inputBitShares[0][privacyPeerIndex][inputIndex2], 0, data, bitsCount+1, bitsCount);
						primitives[privacyPeerIndex].bitwiseLessThan(nextID, data);
					}
				}
			}
			// compute bitwise less than of a < [b] (only b is bitwise shared)
			int typeIndex = 1;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					long[] publicValue = primitives[privacyPeerIndex].getBits(input[inputIndex]);
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = typeIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
						operationIDs[nextID] = nextID;
						data = new long[1+2*bitsCount];
						data[0] = typeIndex;
						System.arraycopy(publicValue, 0, data, 1, bitsCount);
						System.arraycopy(inputBitShares[0][privacyPeerIndex][inputIndex2], 0, data, bitsCount+1, bitsCount);
						primitives[privacyPeerIndex].bitwiseLessThan(nextID, data);
					}
				}
			}
			// compute bitwise less than of [a] < b (only a is bitwise shared)
			typeIndex = 2;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						long[] publicValue = primitives[privacyPeerIndex].getBits(input[inputIndex2]);
						nextID = typeIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
						operationIDs[nextID] = nextID;
						data = new long[1+2*bitsCount];
						data[0] = typeIndex;
						System.arraycopy(inputBitShares[0][privacyPeerIndex][inputIndex], 0, data, 1, bitsCount);
						System.arraycopy(publicValue, 0, data, bitsCount+1, bitsCount);
						primitives[privacyPeerIndex].bitwiseLessThan(nextID, data);
					}
				}
			}
			doOperation(operationIDs);

			// get bitwise less than results
			long[][][][] bitwiseLessThanResults = new long[numberOfPrivacyPeers][typesCount][input.length][input.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(typeIndex = 0; typeIndex < typesCount; typeIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = typeIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							bitwiseLessThanResults[privacyPeerIndex][typeIndex][inputIndex][inputIndex2] = primitives[privacyPeerIndex].getResult(nextID)[0];
						}
					}
				}
			}

			// reconstruct bitwise less than results
			operationIDs = new int[typesCount*input.length*input.length];
			nextID = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(typeIndex = 0; typeIndex < typesCount; typeIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = typeIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							operationIDs[nextID] = nextID;
							data = new long[1];
							data[0] = bitwiseLessThanResults[privacyPeerIndex][typeIndex][inputIndex][inputIndex2];
							primitives[privacyPeerIndex].reconstruct(nextID, data);
						}
					}
				}
			}
			doOperation(operationIDs);

			// get results
			long[][][][] computedResults = new long[numberOfPrivacyPeers][typesCount][input.length][input.length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(typeIndex = 0; typeIndex < typesCount; typeIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							nextID = typeIndex*(input.length*input.length) + inputIndex*input.length + inputIndex2;
							computedResults[privacyPeerIndex][typeIndex][inputIndex][inputIndex2] = primitives[privacyPeerIndex].getResult(nextID)[0];
						}
					}
				}
			}

			// check results
			long realResult = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(typeIndex = 0; typeIndex < typesCount; typeIndex++) {
					for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
						for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
							if(input[inputIndex] < input[inputIndex2]) {
								realResult = 1;
							} else {
								realResult = 0;
							}
							assertEquals("checking bitwise less-than "+input[inputIndex]+" < "+input[inputIndex2]+" of type="+typeIndex+" (in field of size "+fieldSize+"): ", realResult, computedResults[privacyPeerIndex][typeIndex][inputIndex][inputIndex2]);
						}
					}
				}
			}
		}
	}
}
