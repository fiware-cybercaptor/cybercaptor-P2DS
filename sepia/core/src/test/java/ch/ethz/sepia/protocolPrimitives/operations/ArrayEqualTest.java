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

public class ArrayEqualTest extends OperationsTest{
	
	public ArrayEqualTest(String name){
		super.setName(name);
	}
	
	
	private long[][] inputValues = null;
	
	private int VALUES = 80;
	
	@Override
	protected void createInputValues() {
		// make sure that some of the entries are equal
		double overlap = 0.4;
		inputValues = new long[numberOfPeers][VALUES];
		int shared = (int)(VALUES*overlap);
		
		long[] intersection = new long[shared];
		
		for(int i = 0; i < shared; i++){
			// avoid negative numbers
			long randomValue = random.nextLong() % fieldSize;
			if(randomValue < 0) {
				randomValue = 0-randomValue;
			}
			intersection[i] = randomValue;
		}
		
		for(int i = 0; i < numberOfPeers; i++){
			System.arraycopy(intersection, 0, inputValues[i], 0, shared);
			for(int kk = shared; kk < VALUES; kk++){
				// avoid negative numbers
				long randomValue = random.nextLong() % fieldSize;
				if(randomValue < 0) {
					randomValue = 0-randomValue;
				}
				inputValues[i][kk] = randomValue;
			}
		}
	}
	
	@Override
	protected void createInputShares(){
		// create Shamir shares of input values
		inputShares = new long[numberOfPeers][numberOfPrivacyPeers][inputValues[0].length];
		for(int i = 0; i < numberOfPeers; i++){
			inputShares[i] = mpcShamirSharingPeers[i].generateShares(inputValues[i]);
		}
	}
	
	
	public void testArrayEqual(){
		numberOfPeers = 2; // don't change this! we only compare 2 arrays
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
				long[][] shrs = new long[numberOfPeers][inputValues[0].length];
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				// prepare input shares
				for(int p = 0; p < numberOfPeers; p++){
					shrs[p] = inputShares[p][privacyPeerIndex];
				}
				
				primitives[privacyPeerIndex].arrayEqual(operationIDs[0], shrs[0], shrs[1]);
			}

			doOperation(operationIDs);
			
			// get intersection results
			long[][] arrayEqualResults = new long[numberOfPrivacyPeers][inputValues[0].length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {	
				arrayEqualResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
			}
			
			// reconstruct intersection results
			operationIDs = new int[inputValues[0].length];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int position = 0; position < inputValues[0].length; position++){
					operationIDs[position] = position;
					data = new long[1];
					data[0] = arrayEqualResults[privacyPeerIndex][position];
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
			long realResult[] = doArrayEqual();
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < inputValues[0].length; position++){
					assertEquals("checking Equal of Ppeer "+privacyPeerIndex+
							" in field of size "+fieldSize+" at position "+position+": ", 
							realResult[position], computedResults[privacyPeerIndex][position]);
				}

			}

			System.out.println("finished ArrayEqual.");
		}
	}
	
	
	public static void main(String [] args){
		junit.textui.TestRunner.
					run(new ArrayEqualTest("testArrayEqual"));
	}
	
	public long[] doArrayEqual(){
		long[] result = new long[inputValues[0].length];
		for(int i = 0; i < inputValues[0].length; i++){
			if(inputValues[0][i] == inputValues[1][i]){
				result[i] = 1;
			}else{
				result[i] = 0;
			}
		}
		return result;
	}
}
