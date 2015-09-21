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

import java.net.InetAddress;
import java.net.UnknownHostException;

import ch.ethz.sepia.services.BloomFilter;

public class BloomFilterUnionTest extends OperationsTest{
	
	public BloomFilterUnionTest(String name){
		super.setName(name);
	}
	
	/**
	 * Converts an integer to a byte array
	 * @param val value to convert
	 * @return [0]..[3] MSB to LSB representation of val
	 */
    public static byte[] IntToByteArr(long val) {
        byte[] buffer = new byte[4];
   
        // >>> is unsigned  and >> signed bitshift
        buffer[0] = (byte) (val >>> 24);
        buffer[1] = (byte) (val >>> 16);
        buffer[2] = (byte) (val >>> 8);
        buffer[3] = (byte) val;
        
        return buffer;
    }
	
	protected BloomFilter[] nonCountingFilters = null;
	protected BloomFilter[] countingFilters = null;
	
	protected long[] intersection = null;
	/** [0] BloomFilter size, [1] hash functions, [2] fpr*/
	protected double [] params = {32,1,0.2};
	
	protected final int ELEMENTS = 10;
	protected final double FPR = 0.01;
	protected final boolean useStringInputs = true;
	
	protected String[] inputStrings;

	@Override
	protected void createInputValues() {
		// create "size" input values with 30% overlap
		double overlap = 0.1;
		input = new long[ELEMENTS*numberOfPeers];
		inputStrings = new String[ELEMENTS*numberOfPeers];
		int shared = (int)(ELEMENTS*overlap);
		
		intersection = new long[shared];
		
		for(int i = 0; i < shared; i++){
			intersection[i] = i;
		}
		
		for(int i = 0; i < numberOfPeers; i++){
			System.arraycopy(intersection, 0, input, i*ELEMENTS, shared);
			for(int kk = i*ELEMENTS+shared; kk < (i+1)*ELEMENTS; kk++){
				input[kk] = kk-i*shared;
			}
		}
		// conversion to IPv4 Adresses
		for(int i = 0; i < input.length; i++){
			try {
				inputStrings[i] = InetAddress.getByAddress(IntToByteArr(input[i]))
									.getHostAddress();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
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
			// debugging
//			System.out.println("Input Values:");
			
			for(int kk = 0; kk < ELEMENTS; kk++){
				if(useStringInputs){
					nonCountingFilters[i].insert(inputStrings[i*ELEMENTS+kk]);
					countingFilters[i].insert(inputStrings[i*ELEMENTS+kk]);
//					System.out.println(inputStrings[i*ELEMENTS+kk]);
				}else{
					nonCountingFilters[i].insert((int)input[i*ELEMENTS+kk]);
					countingFilters[i].insert((int)input[i*ELEMENTS+kk]);
//					System.out.println(input[i*ELEMENTS+kk]);
				}
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
	 * Test method for {@link mpc.protocolPrimitives.Primitives#bfUnion(int, long[][], boolean)}.
	 * <p>
	 * Tests if result of MPC BloomFilter union is equal to result
	 * of normal union.
	 */
	public void testBfUnionNonCounting(){
		numberOfPeers = 25;
		
		for(int groupOrderIndex = 2; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting NC Union operation with group order=" + fieldSize);
			
			
			// compute union of inputs nonCounting
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
				primitives[privacyPeerIndex].bfUnion(operationIDs[0], shrs, false);
			}

			doOperation(operationIDs);
			
			// get union results
			long[][] unionResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					unionResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
				//}
			}
			
			// reconstruct union results
			operationIDs = new int[(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
//				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						operationIDs[position] = position;
						data = new long[1];
						data[0] = unionResults[privacyPeerIndex][position];
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
			long realResult[] = doUnion(false); // non-counting
			
			
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				for(int position = 0; position < params[0]; position++){
					assertEquals("checking intersection of Ppeer "+privacyPeerIndex+
							" in field of size "+fieldSize+": ", 
							realResult[position]%fieldSize, computedResults[privacyPeerIndex][position]);
				}
			}
			
			System.out.println("finished non-counting");
		
		}
	}
	
	public void testBfUnionCounting(){
		numberOfPeers = 3;
		
		for(int groupOrderIndex = 0; groupOrderIndex < fieldSizes.length; groupOrderIndex++) {
			fieldSize = fieldSizes[groupOrderIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting counting Union operation with group order=" + fieldSize);
			
			
			// compute union of inputs nonCounting
			int[] operationIDs = new int[1];
			long[] data = null;
			
			
			// compute union of inputs counting
			operationIDs = new int[1];
			data = null;
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
				primitives[privacyPeerIndex].bfUnion(operationIDs[0], shrs, true);
			}

			doOperation(operationIDs);
			
			// get union results
			long [][] unionResults = new long[numberOfPrivacyPeers][(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				//for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					unionResults[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
				//}
			}
			
			// reconstruct union results
			operationIDs = new int[(int)params[0]];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
//				for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
					for(int position = 0; position < params[0]; position++){
						operationIDs[position] = position;
						data = new long[1];
						data[0] = unionResults[privacyPeerIndex][position];
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
//					BloomFilter computedBF = new BloomFilter((int)params[1], computedResults[privacyPeerIndex], true);
//					System.out.println("mpcUnion:\n"+computedBF.toString());
				//}
			}
			
			System.out.println("Checking");

			// assert that results of reconstruction are equal to original input
			long[] realResult = doUnion(true); // counting
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
	 * Computes the union of all input BloomFilters
	 * @param cnt specifies wether counting or non-counting filters were used
	 * @return bloom filter union result as long array
	 */
	protected long[] doUnion(boolean cnt){
		BloomFilter un = null;
		if(cnt){
			un = BloomFilter.union(countingFilters, cnt);
		}else{
			un = BloomFilter.union(nonCountingFilters, cnt);
		}

		// debugging
//		System.out.println("plaintext union:\n"+un.toString());
		
		long res[] = new long[un.getArray().length];
		for(int i = 0; i< res.length; i++){
			res[i] = un.getArray()[i];
		}
		return res;
	}
	
	
	public static void main(String [] args){
		junit.textui.TestRunner.
				run(BloomFilterUnionTest.class);
	}
	
}
