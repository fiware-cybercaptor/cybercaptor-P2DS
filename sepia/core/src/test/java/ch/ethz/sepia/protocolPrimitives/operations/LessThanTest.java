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

import ch.ethz.sepia.services.Utils;


/**
 * Test cases for LessThan operation.
 *
 * @author Dilip Many
 *
 */
public class LessThanTest extends OperationsTest {
	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#lessThan(int, long[])}.
	 * <p>
	 * Computes if some value is less than another using the MPC operation and then tests it against the result using the normal "<".
	 */
	public void testLessThan() {
		float[] failureRates = new float[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting less than operation with field size=" + fieldSize);

			// compute less than of inputs
			int[] operationIDs = new int[input.length*input.length];
			int nextID = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = inputIndex*input.length +inputIndex2;
						operationIDs[nextID] = nextID;
						data = new long[5];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = inputShares[0][privacyPeerIndex][inputIndex2];
						data[2] = -1;
						data[3] = -1;
						data[4] = -1;
						primitives[privacyPeerIndex].lessThan(nextID, data);
					}
				}
			}
			doOperation(operationIDs);

			failureRates[fieldSizeIndex] = verifyLessThanResults();
			System.out.println("computation of less-than (in field of size="+fieldSize+") failed in "+(failureRates[fieldSizeIndex]*100)+"% of tries");
		}
		System.out.println("\nless-than operation failure rates (field size, failure rate):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failureRates[i]);
		}
	}


	/**
	 * verifies the MPC less-than operation results
	 *
	 * @return	the percentage of less-than operations that failed
	 */
	private float verifyLessThanResults() {
		// get less-than results (and count number of successful less-than computations)
		long[][][] lessThanResults = new long[numberOfPrivacyPeers][input.length][input.length];
		int nextID = 0;
		int lessThanCount = 0;
		for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			lessThanCount = 0;
			for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
				for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
					nextID = inputIndex*input.length +inputIndex2;
					lessThanResults[privacyPeerIndex][inputIndex][inputIndex2] = primitives[privacyPeerIndex].getResult(nextID)[0];
					if(lessThanResults[privacyPeerIndex][inputIndex][inputIndex2] != -1L) {
						lessThanCount++;
					}
				}
			}
		}

		if(lessThanCount==0) {
			return 1;
		}

		// reconstruct less-than results
		int[] operationIDs = new int[lessThanCount];
		long[] data = null;
		for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			primitives[privacyPeerIndex].initialize(operationIDs.length);
			nextID = 0;
			for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
				for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
					if(lessThanResults[privacyPeerIndex][inputIndex][inputIndex2] != -1L) {
						operationIDs[nextID] = nextID;
						data = new long[1];
						data[0] = lessThanResults[privacyPeerIndex][inputIndex][inputIndex2];
						primitives[privacyPeerIndex].reconstruct(nextID, data);
						nextID++;
					}
				}
			}
		}
		doOperation(operationIDs);

		// get results
		long[][] computedResults = new long[numberOfPrivacyPeers][lessThanCount];
		for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			for(int lessThanResultIndex = 0; lessThanResultIndex < lessThanCount; lessThanResultIndex++) {
				computedResults[privacyPeerIndex][lessThanResultIndex] = primitives[privacyPeerIndex].getResult(lessThanResultIndex)[0];
			}
		}

		// assert that results are correct
		long realResult = 0;
		for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			nextID = 0;
			for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
				for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
					if(lessThanResults[privacyPeerIndex][inputIndex][inputIndex2] != -1L) {
						if(input[inputIndex] < input[inputIndex2]) {
							realResult = 1;
						} else {
							realResult = 0;
						}
						assertEquals("checking less-than result of "+input[inputIndex]+" < "+input[inputIndex2]+" (in field of size "+fieldSize+"): ", realResult, computedResults[privacyPeerIndex][nextID]);
						nextID++;
					}
				}
			}
		}
		return (float) ((input.length*input.length) - lessThanCount) / (float) (input.length*input.length);
	}


	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#lessThan(int, long[])}.
	 * <p>
	 * Computes if some value is less than another using the MPC operation and then tests it against the result using the normal "<".
	 * a<b, for which b < fieldSize/2 is known and 2 bitwise shared random numbers are pre-generated
	 */
	public void testLessThan2() {
		float[] failureRates = new float[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting less than operation with field size=" + fieldSize);

			// generate bitwise shared random numbers
			int[] operationIDs = new int[1];
			operationIDs[0] = 0;
			long[] data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(1);
				data = new long[1];
				data[0] = 2*input.length*input.length;
				primitives[privacyPeerIndex].batchGenerateBitwiseRandomNumbers(0, data);
			}
			doOperation(operationIDs);

			// get pre-generated bitwise shared random numbers
			long[][] preGeneratedRandomNumbers = new long[numberOfPrivacyPeers][];
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				preGeneratedRandomNumbers[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
			}

			// compute less than of inputs
			operationIDs = new int[input.length*input.length];
			int nextID = 0;
			int bitsCount = 2*primitives[0].getBitsCount();
			int dataSize = 5+bitsCount;
			data = null;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(operationIDs.length);
				int nextBit = 0;
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = inputIndex*input.length +inputIndex2;
						operationIDs[nextID] = nextID;
						data = new long[dataSize];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = input[inputIndex2];
						data[2] = -1;
						data[3] = (input[inputIndex2] < (fieldSize+1)/2 ? 1 : 0); // (it's "< (fieldSize+1)/2" because we have an integer division; not real numbers)
						data[4] = -1;
						// (use 2 pre-generated bitwise shared random numbers)
						System.arraycopy(preGeneratedRandomNumbers[privacyPeerIndex], nextBit, data, 5, bitsCount);
						nextBit += bitsCount;
						primitives[privacyPeerIndex].lessThan(nextID, data);
//						primitives[privacyPeerIndex].setOperationDescription(nextID, "less-than of "+input[inputIndex]+"<"+input[inputIndex2]);
					}
				}
			}
			doOperation(operationIDs);

//			writeLogs(operationIDs);

			failureRates[fieldSizeIndex] = verifyLessThanResults();
			System.out.println("computation of less-than (in field of size="+fieldSize+") failed in "+(failureRates[fieldSizeIndex]*100)+"% of tries");
		}
		System.out.println("\nless-than operation failure rates (field size, failure rate):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failureRates[i]);
		}
	}

	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#lessThan(int, long[])} using automatic generation of bitwise share random numbers.
	 */
	public void testLessThanIntegrated() {
		float[] failureRates = new float[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting less than operation with field size=" + fieldSize);

			// compute less than of inputs
			int nextID = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(input.length*input.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = inputIndex*input.length +inputIndex2;
						long[] data = new long[5];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = input[inputIndex2];
						data[2] = -1;
						data[3] = (input[inputIndex2] < (fieldSize+1)/2 ? 1 : 0); // (it's "< (fieldSize+1)/2" because we have an integer division; not real numbers)
						data[4] = -1;
						primitives[privacyPeerIndex].lessThan(nextID, data);
					}
				}
			}
			doOperationIntegrated();

			failureRates[fieldSizeIndex] = verifyLessThanResults();
			System.out.println("computation of less-than (in field of size="+fieldSize+") failed in "+(failureRates[fieldSizeIndex]*100)+"% of tries");
		}
		System.out.println("\nless-than operation failure rates (field size, failure rate):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failureRates[i]);
		}
	}


	/**
	 * Test method for {@link mpc.protocolPrimitives.Primitives#lessThan(int, long[])} using predicate caching.
	 * It should run faster than the other variants.
	 */
	public void testLessThanCaching() {
		float[] failureRates = new float[fieldSizes.length];
		float[] uncachedTimes = new float[fieldSizes.length];
		float[] cachedTimes = new float[fieldSizes.length];
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			createInputShares();
			System.out.println("\ntesting less than operation with field size=" + fieldSize);

			// -------------------------------------------
			// First run. No cache hits
			// -------------------------------------------
			
			long start = System.currentTimeMillis();
			// compute less than of inputs
			int nextID = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(input.length*input.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = inputIndex*input.length +inputIndex2;
						long[] data = new long[5];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = input[inputIndex2];
						data[2] = -1;
						data[3] = (input[inputIndex2] < (fieldSize+1)/2 ? 1 : 0); // (it's "< (fieldSize+1)/2" because we have an integer division; not real numbers)
						data[4] = -1;
						String keyA = "Input-"+inputIndex;
						String keyB = null; // no secret
						String keyAB = "Difference-"+inputIndex+"-"+inputIndex2;
						primitives[privacyPeerIndex].lessThan(nextID, data, keyA, keyB, keyAB);
					}
				}
			}
			doOperationIntegrated();

			failureRates[fieldSizeIndex] = verifyLessThanResults();
			System.out.println("computation of less-than (in field of size="+fieldSize+") failed in "+(failureRates[fieldSizeIndex]*100)+"% of tries");
			uncachedTimes[fieldSizeIndex] = System.currentTimeMillis()-start;  
			System.out.println("Duration: "+uncachedTimes[fieldSizeIndex]+"ms");

			// -------------------------------------------
			// Second run. All predicates should be in the cache.
			// -------------------------------------------
			start = System.currentTimeMillis();
			// compute less than of inputs
			nextID = 0;
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				primitives[privacyPeerIndex].initialize(input.length*input.length);
				for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
					for(int inputIndex2 = 0; inputIndex2 < input.length; inputIndex2++) {
						nextID = inputIndex*input.length +inputIndex2;
						long[] data = new long[5];
						data[0] = inputShares[0][privacyPeerIndex][inputIndex];
						data[1] = input[inputIndex2];
						data[2] = -1;
						data[3] = (input[inputIndex2] < (fieldSize+1)/2 ? 1 : 0); // (it's "< (fieldSize+1)/2" because we have an integer division; not real numbers)
						data[4] = -1;
						String keyA = "Input-"+inputIndex;
						String keyB = null; // no secret
						String keyAB = "Difference-"+inputIndex+"-"+inputIndex2;
						primitives[privacyPeerIndex].lessThan(nextID, data, keyA, keyB, keyAB);
					}
				}
			}
			doOperationIntegrated();

			failureRates[fieldSizeIndex] = verifyLessThanResults();
			System.out.println("computation of less-than (in field of size="+fieldSize+") failed in "+(failureRates[fieldSizeIndex]*100)+"% of tries");
			cachedTimes[fieldSizeIndex] = System.currentTimeMillis()-start;  
			System.out.println("Duration: "+cachedTimes[fieldSizeIndex]+"ms");

		}
		System.out.println("\nless-than operation failure rates (field size, failure rate):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(fieldSizes[i]+"\t"+failureRates[i]);
		}
		System.out.println("\nRunning time [ms] (1st run (uncached), 2nd run (cached):");
		for(int i = 0; i < fieldSizes.length; i++) {
			System.out.println(uncachedTimes[i]+"\t"+cachedTimes[i]);
		}

	}

	
	/**
	 * executes the operation: simulates sending and receiving of shares, processes
	 * the data and repeats if the operation hasn't completed yet.
	 * 
	 * If LessThan operations require random numbers, they are batch generated before.
	 *
	 * @return
	 */
	protected boolean doOperationIntegrated() {
		System.out.println("doOperation will process "+primitives[0].getOperations().size()+" operations");

		generateRandomNumbersIfNeeded();

		int roundNumber = 1;
		boolean areOperationsCompleted = false;
		
		// process the initial data
		try {
			for(int i = 0; i < numberOfPrivacyPeers; i++) {
				primitives[i].processReceivedData();
			}
		}
		catch (Exception e) {
			fail("An exception occured when processing the initial data: " +Utils.getStackTrace(e));
		}
		// check if operations have completed already
		areOperationsCompleted = true;
		for(int i = 0; i < numberOfPrivacyPeers; i++) {
			areOperationsCompleted = areOperationsCompleted && primitives[i].areOperationsCompleted();
		}

		while(!areOperationsCompleted) {
			// send (copy) the data
			long[] dataToSend = null;
			for(int i = 0; i < numberOfPrivacyPeers; i++) {
				for(int j = 0; j < numberOfPrivacyPeers; j++) {
					if(j != i) {
						// "i sends shares to j" and "j receives shares from i"
						dataToSend = primitives[i].getDataToSend(j);
						assertNotNull("data to send is null! (from privacy peer "+i+", in round "+roundNumber+")", dataToSend);
						assertTrue("no data to send! (from privacy peer "+i+", in round "+roundNumber+")", dataToSend.length > 0);
						primitives[j].setReceivedData(i, dataToSend);
					}
				}
			}
			// process the data
			try {
				for(int i = 0; i < numberOfPrivacyPeers; i++) {
					primitives[i].processReceivedData();
				}
			}
			catch (Exception e) {
				fail("An exception occured when processing the received data: " +Utils.getStackTrace(e));
			}

			// check if operations have completed yet
			areOperationsCompleted = true;
			for(int i = 0; i < numberOfPrivacyPeers; i++) {
				areOperationsCompleted = areOperationsCompleted && primitives[i].areOperationsCompleted();
			}
			roundNumber++;
		}

		return true;
	}

	
	
	// TODO: test 62 other less-than variants...
}
