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

public class BloomFilterIntersectionTest extends OperationsTest{
	
	public BloomFilterIntersectionTest(String name){
		super.setName(name);
	}
	
	protected BloomFilter[] nonCountingFilters = null;
	protected BloomFilter[] countingFilters = null;
	
	protected long[] intersection = null;
	/** [0] BloomFilter size, [1] hash functions, [2] fpr*/
	protected double [] params = {128,1,1};

	// min(a,b) % q  is not necessary the same as min(a%q, b%q)
	// e.g. a=13 b =15  q=7  
	//min(a,b) % q = 6  // 
	//min(a%q, b%q) = 1 // this is computed by BloomFilterInterseciton
	protected final int ELEMENTS = 1; 
	protected final double FPR = 0.01;

	@Override
	protected void createInputValues() {
		// create "size" input values with 30% overlap
		double overlap = 0.4;
		input = new long[ELEMENTS*numberOfPeers];
		int shared = (int)(ELEMENTS*overlap);
		
		intersection = new long[shared];
		
		for(int i = 0; i < shared; i++){
			intersection[i] = i;
		}
		
		for(int i = 0; i < numberOfPeers; i++){
			System.arraycopy(intersection, 0, input, i*ELEMENTS, shared);
			for(int kk = i*ELEMENTS+shared; kk < (i+1)*ELEMENTS; kk++){
				input[kk] = kk;
			}
		}
	}
	

	protected void createInputShares(){
		// prepare BloomFilters
		nonCountingFilters = new BloomFilter[numberOfPeers];
		countingFilters = new BloomFilter[numberOfPeers];
		//params = BloomFilter.getParameterEstimate(ELEMENTS, FPR);
		System.out.println("BloomFilter size: "+params[0]+", hash funcitons: "+params[1]+", actual FPR: "+params[2]);
		for(int i = 0; i < numberOfPeers; i++){
			nonCountingFilters[i] = new BloomFilter((int)params[1], (int)params[0], false);
			countingFilters[i] = new BloomFilter((int)params[1], (int)params[0], true);
		}
		
		// insert elements
		for(int i = 0; i < numberOfPeers; i++){
			for(int kk = 0; kk < ELEMENTS; kk++){
				nonCountingFilters[i].insert((int)input[i*ELEMENTS+kk]);
				countingFilters[i].insert((int)input[i*ELEMENTS+kk]);
			}
			
			// uncomment for debugging ( use only 1 or 2 elemtens to decrease filter length)
//			System.out.println("input filters:");
//			System.out.println(nonCountingFilters[i].toString());
//			System.out.println(countingFilters[i].toString());
			
		}
		
		// create Shamir shares of input values
		inputShares = new long[numberOfPeers][numberOfPrivacyPeers][2*(int)params[0]];
		for(int i = 0; i < numberOfPeers; i++) {
			long [] data = new long[2*(int)params[0]];
			for(int j = 0; j < (int)params[0]; j++){ // j = 0 - Bloomfilterlength
				data[j] = nonCountingFilters[i].getArray()[j];
				data[(int)params[0]+j]= countingFilters[i].getArray()[j];
			}
			inputShares[i] = mpcShamirSharingPeers[i].generateShares(data);
		}
	}
	
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#bfIntersection(int, long[][], boolean)}.
	 * <p>
	 * Tests if result of MPC BloomFilter intersection is equal to result
	 * of normal intersection.
	 * 
	 */
	public void testBfIntersectionNonCounting(){
		numberOfPeers = 8;
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting bfIntersection operation with group order=" + fieldSize);
			
			
			// compute intersection of inputs nonCounting
			int[] operationIDs = new int[1];
			long[] data = null;

			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				// in each privacy peer collect the data from all input peers
				
				// create new array each time!! else we would overwrite the shares we just assigned to the previous operation
				long[][] shrs = new long[numberOfPeers][(int)params[0]];
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++){
					// prepare input shares
					for(int kk = 0; kk < params[0]; kk++){
						//shrs[kk][peerIndex]= inputShares[peerIndex][privacyPeerIndex][(int)params[0] + kk];
						shrs[peerIndex][kk]= inputShares[peerIndex][privacyPeerIndex][kk];
					}
				}
				primitives[privacyPeerIndex].bfIntersection(operationIDs[0], shrs, false);
			}

			doOperation(operationIDs);
			
			// get intersection results
			long[][] intersectionResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					intersectionResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
				//}
			}
			
			// reconstruct intersection results
			operationIDs = new int[(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
//				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						operationIDs[position] = position;
						data = new long[1];
						data[0] = intersectionResults[privacyPeerIndex][position];
						primitives[privacyPeerIndex].reconstruct(operationIDs[position], data);
					}
//				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][] computedResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						// every privacy peer opens towards every input peer
						computedResults[privacyPeerIndex][position] = primitives[privacyPeerIndex].getResult(position)[0];
					}
					
					// debugging 
//					BloomFilter computedBF = new BloomFilter((int)params[1], computedResults[privacyPeerIndex], false);
//					System.out.println(computedBF.toString());
				//}
			}
			
			System.out.println("Checking");

			// assert that results of reconstruction are equal to original input
			long realResult[] = doIntersection(false); // non-counting
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						assertEquals("checking intersection of Ppeer "+privacyPeerIndex+
								" in field of size "+fieldSize+": ", 
								realResult[position]%fieldSize, computedResults[privacyPeerIndex][position]);
					}
				//}
			}
			
			System.out.println("finished non-counting.");
		}
	}
	
	public void testBfIntersectionCounting(){
		numberOfPeers = 3;
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting counting bfIntersection operation with group order=" + fieldSize);
			
			
			//////////////////////////////////////////////////////////////////////////////
			// same thing for counting filter
			//////////////////////////////////////////////////////////////////////////////
			
			// compute intersection of inputs counting
			int[] operationIDs = new int[1];
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				// in each privacy peer collect the data from all input peers
				
				// create new array each time!! else we would overwrite the shares we just assigned to the previous operation
				long[][] shrs = new long[numberOfPeers][(int)params[0]];
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++){
					// prepare input shares
					for(int kk = 0; kk < params[0]; kk++){
						// use 2nd half of input shares this time
						shrs[peerIndex][kk]= inputShares[peerIndex][privacyPeerIndex][(int)params[0] + kk];
						//shrs[kk][peerIndex]= inputShares[peerIndex][privacyPeerIndex][kk];
					}
				}
				primitives[privacyPeerIndex].bfIntersection(operationIDs[0], shrs, true);
			}

			doOperation(operationIDs);
			
			// get intersection results
			long[][] intersectionResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					intersectionResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
				//}
			}
			
			// reconstruct intersection results
			operationIDs = new int[(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
//				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						operationIDs[position] = position;
						data = new long[1];
						data[0] = intersectionResults[privacyPeerIndex][position];
						primitives[privacyPeerIndex].reconstruct(operationIDs[position], data);
					}
//				}
			}
			
			doOperation(operationIDs);
			
			// get reconstructed results
			long[][]computedResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						// every privacy peer opens towards every input peer
						computedResults[privacyPeerIndex][position] = primitives[privacyPeerIndex].getResult(position)[0];
					}
					
					// debugging 
//					BloomFilter computedBF = new BloomFilter((int)params[1], computedResults[privacyPeerIndex], false);
//					System.out.println(computedBF.toString());
				//}
			}
			
			System.out.println("Checking");

			// assert that results of reconstruction are equal to original input
			long []realResult = doIntersection(true); // non-counting
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						assertEquals("checking intersection of Ppeer "+privacyPeerIndex+
								" in field of size "+fieldSize+": ", 
								realResult[position]%fieldSize, computedResults[privacyPeerIndex][position]);
					}
				//}
			}
			System.out.println("finished counting");
		}
	}
	
	/**
	 * Computes the intersection of all input BloomFilters
	 * @param cnt specifies wether counting or non-counting filters were used
	 * @return bloom filter intersection result as long array
	 */
	protected long[] doIntersection(boolean cnt){
		BloomFilter bf = null;
		if(cnt){
			bf = BloomFilter.intersection(countingFilters, cnt);
		}else{
			bf = BloomFilter.intersection(nonCountingFilters, cnt);
		}
		
		long[] data = new long[bf.getArray().length];
		// debug
//		System.out.println("result filter:");
//		System.out.println(bf.toString());
		for(int i = 0; i < data.length; i++){
			data[i] = bf.getArray()[i];
		}
		return data;
		
	}
	
	public static void main(String [] args){
		junit.textui.TestRunner.
				run(new BloomFilterIntersectionTest("testBfIntersectionNonCounting"));
	}
	
}
