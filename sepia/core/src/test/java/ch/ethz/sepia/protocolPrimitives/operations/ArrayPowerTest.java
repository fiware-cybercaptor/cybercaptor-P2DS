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


public class ArrayPowerTest  extends OperationsTest {
	
	public ArrayPowerTest(String name){
		super.setName(name);
	}
	
	/** Format [peerIndex][value] 	 */
	private long[][] inputValues = null;
	
	private long exponent;
	
	@Override
	protected void createInputValues() {
		inputValues = new long[numberOfPeers][100];
		
		for(int i = 0; i < inputValues.length; i++){
			for(int j = 0; j < inputValues[0].length; j++){
				long randomValue = random.nextLong() % fieldSize;
				if(randomValue < 0) {
					randomValue = 0-randomValue;
				}
				inputValues[i][j] = randomValue;
			}
		}
		
		long randomValue = random.nextLong() % fieldSize;
		if(randomValue < 0) {
			randomValue = 0-randomValue;
		}
		exponent = randomValue;
	}
	
	@Override
	protected void createInputShares(){
		// create Shamir shares of input values
		inputShares = new long[numberOfPeers][numberOfPrivacyPeers][inputValues[0].length];
		for(int i = 0; i < numberOfPeers; i++){
			inputShares[i] = mpcShamirSharingPeers[i].generateShares(inputValues[i]);
		}
	}
	
	public void testArrayPower(){
		numberOfPeers = 1; // current unit test only works with 1 array to power
		// if you want to compute the power of more than 1 player array you need 
		// to change some of the code below
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting multiplication operation with group order=" + fieldSize);
			
			// compute intersection of inputs nonCounting
			int[] operationIDs = new int[1];
			long[] data = null;

			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				// in each privacy peer collect the data from all input peers
				
				// create new array each time!! else we would overwrite the shares we just assigned to the previous operation
				long[] shrs = new long[inputValues[0].length];
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				// prepare input shares
				for(int p = 0; p < numberOfPeers; p++){
					shrs = inputShares[p][privacyPeerIndex];
				}
				
				primitives[privacyPeerIndex].arrayPower(operationIDs[0], shrs, exponent);
			}

			doOperation(operationIDs);
			
			// get intersection results
			long[][] arrayPowerResults = new long[numberOfPrivacyPeers][inputValues[0].length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {	
				arrayPowerResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
			}
			
			// reconstruct intersection results
			operationIDs = new int[inputValues[0].length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int position = 0; position < inputValues[0].length; position++){
					operationIDs[position] = position;
					data = new long[1];
					data[0] = arrayPowerResults[privacyPeerIndex][position];
					primitives[privacyPeerIndex].reconstruct(operationIDs[position], data);
				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][] computedResults = new long[numberOfPrivacyPeers][inputValues[0].length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < inputValues[0].length; position++){
					// every privacy peer opens towards every input peer
					computedResults[privacyPeerIndex][position] = primitives[privacyPeerIndex].getResult(position)[0];
				}
			}

			System.out.println("Checking");

			// assert that results of reconstruction are equal to original input
			long realResult[][] = doArrayPower();
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < inputValues[0].length; position++){
					assertEquals("checking multiplication of Ppeer "+privacyPeerIndex+
							" in field of size "+fieldSize+": ", 
							realResult[0][position], computedResults[privacyPeerIndex][position]);
				}

			}

			System.out.println("finished ArrayPower.");
		}
		
	}
	
	
	public static void main(String [] args){
		junit.textui.TestRunner.
					run(new ArrayPowerTest("testArrayPower"));
	}
	
	/**
	 * 
	 * @return power of the input arrays [inputpeer][arrayindex]
	 */
	public long[][] doArrayPower(){
		long[][] result = new long[numberOfPeers][inputValues[0].length];
		for (int i = 0; i < numberOfPeers; i++){
			for(int j = 0; j < inputValues[0].length; j++){
				result[i][j] = mpcShamirSharingPeers[i].fastExponentiation(inputValues[i][j], exponent);
			}
		}
		return result;
	}
}
