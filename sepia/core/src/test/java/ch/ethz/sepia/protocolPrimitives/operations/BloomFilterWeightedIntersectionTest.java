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

import ch.ethz.sepia.services.BloomFilter;

public class BloomFilterWeightedIntersectionTest extends OperationsTest{

	public BloomFilterWeightedIntersectionTest(String name){
		super.setName(name);
	}
	
	
	protected BloomFilter[] keyFilters = null;
	protected BloomFilter[] countingFilters = null;
	
	protected long[] weights = null;
	
	protected long[][][] inputWeightShares = null;
	
	/** [0] BloomFilter size, [1] hash functions, [2] fpr*/
	protected double [] params = {128,1,1};

	// min(a,b) % q  is not necessary the same as min(a%q, b%q)
	// e.g. a=13 b =15  q=7  
	//min(a,b) % q = 6  // 
	//min(a%q, b%q) = 1 // this is computed by BloomFilterInterseciton
	protected final int ELEMENTS = 1; 
	protected final double FPR = 0.01;
	protected final int KTHRES = 3;
	protected final int WTHRES = 3;

	@Override
	protected void createInputValues() {
		// create "size" input values with 30% overlap
		double overlap = 0.4;
		// input = keys
		input = new long[ELEMENTS*numberOfPeers];
		int shared = (int)(ELEMENTS*overlap);
		
		long[] intersection = new long[shared];
		
		for(int i = 0; i < shared; i++){
			intersection[i] = i;
		}
		
		for(int i = 0; i < numberOfPeers; i++){
			System.arraycopy(intersection, 0, input, i*ELEMENTS, shared);
			for(int kk = i*ELEMENTS+shared; kk < (i+1)*ELEMENTS; kk++){
				input[kk] = kk;
			}
		}
		
		weights = new long[ELEMENTS];
		for(int i = 0; i < weights.length; i++){
			weights[i] = 1 + (i % 3); // weights from 0 to 4
		}
		
	}
	

	protected void createInputShares(){
		// prepare BloomFilters
		keyFilters = new BloomFilter[numberOfPeers];
		countingFilters = new BloomFilter[numberOfPeers];
		//params = BloomFilter.getParameterEstimate(ELEMENTS, FPR);
		System.out.println("BloomFilter size: "+params[0]+", hash funcitons: "+params[1]+", actual FPR: "+params[2]);
		for(int i = 0; i < numberOfPeers; i++){
			keyFilters[i] = new BloomFilter((int)params[1], (int)params[0], false);
			countingFilters[i] = new BloomFilter((int)params[1], (int)params[0], true);
		}
		
		// insert elements
		for(int i = 0; i < numberOfPeers; i++){
			for(int kk = 0; kk < ELEMENTS; kk++){
				keyFilters[i].insert((int)input[i*ELEMENTS+kk]);
				// insert weights ( just insert the key 'weight' times into a bf
				for(int j = 0; j < weights[kk]; j++){
					countingFilters[i].insert((int)input[i*ELEMENTS+kk]);
				}
			}
			
			// uncomment for debugging ( use only 1 or 2 elemtens to decrease filter length)
//			System.out.println("input filters:");
//			System.out.println(keyFilters[i].toString());
//			System.out.println(countingFilters[i].toString());
			
		}
		
		// create Shamir shares of input values
		inputShares = new long[numberOfPeers][numberOfPrivacyPeers][(int)params[0]];
		inputWeightShares = new long[numberOfPeers][numberOfPrivacyPeers][(int)params[0]];
		for(int i = 0; i < numberOfPeers; i++) {
			long [] keys = new long[(int)params[0]];
			long [] w = new long[(int)params[0]];
			for(int j = 0; j < (int)params[0]; j++){ // j = 0 - Bloomfilterlength
				keys[j] = keyFilters[i].getArray()[j];
				w[j]= countingFilters[i].getArray()[j];
			}
			inputShares[i] = mpcShamirSharingPeers[i].generateShares(keys);
			inputWeightShares[i] = mpcShamirSharingPeers[i].generateShares(w);
		}
	}
	
	public void testBfWSINoCount(){
		numberOfPeers = 4;
		for(int groupOrderIndex = 2; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting bfThresholdUnion operation with group order=" + fieldSize);
			
			
			// compute intersection of inputs nonCounting
			int[] operationIDs = new int[1];
			long[] data = null;

			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				// in each privacy peer collect the data from all input peers
				
				// create new array each time!! else we would overwrite the shares we just assigned to the previous operation
				long[][] keyshrs = new long[numberOfPeers][(int)params[0]];
				long[][] weightshrs = new long[numberOfPeers][(int)params[0]];
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++){
					// prepare input shares
					keyshrs[peerIndex]= inputShares[peerIndex][privacyPeerIndex];
					weightshrs[peerIndex] = inputWeightShares[peerIndex][privacyPeerIndex];
				}
				primitives[privacyPeerIndex].bfWeightedSetIntersection(operationIDs[0], keyshrs, weightshrs, KTHRES, WTHRES, false);
			}

			doOperation(operationIDs);
			
			// get threshold union
			long[][] WSIResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				WSIResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
			}

			// reconstruct intersection results
			operationIDs = new int[(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int position = 0; position < params[0]; position++){
					operationIDs[position] = position;
					data = new long[1];
					data[0] = WSIResults[privacyPeerIndex][position];
					primitives[privacyPeerIndex].reconstruct(operationIDs[position], data);
				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][] computedResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < params[0]; position++){
					// every privacy peer opens towards every input peer
					computedResults[privacyPeerIndex][position] = primitives[privacyPeerIndex].getResult(position)[0];
				}

				// debugging 
//				BloomFilter computedBF = new BloomFilter((int)params[1], computedResults[privacyPeerIndex], false);
//				System.out.println(computedBF.toString());
			}

			System.out.println("Checking");

			// assert that results of reconstruction are equal to original input
			long realResult[] = doWSI(false); // non-counting
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < params[0]; position++){
					assertEquals("checking weighted set intersection of Ppeer "+privacyPeerIndex+
							" in field of size "+fieldSize+": ", 
							realResult[position]%fieldSize, computedResults[privacyPeerIndex][position]);
				}
			}
		}
		System.out.println("finished non-counting.");
	}
	
	public void testBFWSILearnWeights(){
		numberOfPeers = 4;
		for(int groupOrderIndex = 2; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting bfThresholdUnion operation with group order=" + fieldSize);
			
			
			// compute intersection of inputs nonCounting
			int[] operationIDs = new int[1];
			long[] data = null;

			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				// in each privacy peer collect the data from all input peers
				
				// create new array each time!! else we would overwrite the shares we just assigned to the previous operation
				long[][] keyshrs = new long[numberOfPeers][(int)params[0]];
				long[][] weightshrs = new long[numberOfPeers][(int)params[0]];
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++){
					// prepare input shares
					keyshrs[peerIndex]= inputShares[peerIndex][privacyPeerIndex];
					weightshrs[peerIndex] = inputWeightShares[peerIndex][privacyPeerIndex];
				}
				primitives[privacyPeerIndex].bfWeightedSetIntersection(operationIDs[0], keyshrs, weightshrs, KTHRES, WTHRES, true);
			}

			doOperation(operationIDs);
			
			// get threshold union
			long[][] WSIResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				WSIResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
			}

			// reconstruct intersection results
			operationIDs = new int[(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int position = 0; position < params[0]; position++){
					operationIDs[position] = position;
					data = new long[1];
					data[0] = WSIResults[privacyPeerIndex][position];
					primitives[privacyPeerIndex].reconstruct(operationIDs[position], data);
				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][] computedResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < params[0]; position++){
					// every privacy peer opens towards every input peer
					computedResults[privacyPeerIndex][position] = primitives[privacyPeerIndex].getResult(position)[0];
				}

				// debugging 
//				BloomFilter computedBF = new BloomFilter((int)params[1], computedResults[privacyPeerIndex], false);
//				System.out.println(computedBF.toString());
			}

			System.out.println("Checking");

			// assert that results of reconstruction are equal to original input
			long realResult[] = doWSI(true); // non-counting
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < params[0]; position++){
					assertEquals("checking weighted set intersection of Ppeer "+privacyPeerIndex+
							" in field of size "+fieldSize+": ", 
							realResult[position]%fieldSize, computedResults[privacyPeerIndex][position]);
				}
			}
		}
		System.out.println("finished non-counting.");
	}
	
	public long[] doWSI(boolean cnt){
		BloomFilter res = BloomFilter.weightedIntersection(keyFilters, countingFilters, KTHRES, WTHRES, cnt);
		long[] data = new long[res.getArray().length];
		for(int i = 0; i < data.length; i++){
			data[i] = res.getArray()[i];
		}
		return data;
	}
	
	
	public static void main(String [] args){
		junit.textui.TestRunner.
				run(BloomFilterWeightedIntersectionTest.class);
	}
}
