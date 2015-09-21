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


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import junit.framework.TestCase;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.operationStates.GenericOperationState;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.IOperation;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.LessThan;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Utils;


/**
 * 
 * Class to test the MpcShamirSharingProtocolPrimitives class
 *
 * @author Dilip Many
 *
 */
public class OperationsTest extends TestCase {
    /** Logger to write log messages to */
    private static final Logger logger = LogManager.getLogger(OperationsTest.class);
    
	/** the number of privacy peers */
	protected int numberOfPrivacyPeers = 5;
	/** the number of peers */
	protected int numberOfPeers = 3;
	/** the number of input values from the lower and upper end of the input value range to use in the input */
	protected int borderSize = 6;
	/** the number of random values to use in the input */
	protected int randomValuesCount = 10;
	/** the field sizes used to test with */
	protected long[] fieldSizes = {257, 1153, 4129, 12289, 32833, 65537,
			524353, 8650753, 9999991, 2147352577,
			1111111111111111111L, 3775874107000403461L, 9223372036854775783L};
	/** name of random algorithm (used for Shamir sharing) */
	protected String randomAlgorithm = "SHA1PRNG";

	/** the field size used in the Shamir sharing */
	protected long fieldSize = 1401085391;
	/** random number generator */
	protected Random random = null;
	/** MpcShamirSharing instances for the peers (to use basic operations on Shamir shares) */
	protected ShamirSharing[] mpcShamirSharingPeers = null;
	/** MpcShamirSharingProtocolPrimitives instances for the privacy peers */
	protected Primitives[] primitives = null;
	/** the input values */
	protected long[] input = null;
	/** the Shamir shares of the input values; format: [peerIndex][privacyPeerIndex][inputIndex] */
	protected long[][][] inputShares = null;
	/** the Shamir bit shares of the input values; format: [peerIndex][privacyPeerIndex][inputIndex][bitIndex] */
	protected long[][][][] inputBitShares = null;

	/** log entries of the operations; format: [privacyPeerIndex][operationIndex] */
	protected String[][] operationLogs = null;



	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();

		random = new Random();
	}


	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}



	/**
	 * executes the operations: simulates sending and receiving of shares, processes
	 * the data and repeats if the operations havn't completed yet
	 *
	 * @param operationIDs	the ids of the operations to execute
	 * @return
	 */
	protected boolean doOperation(int[] operationIDs) {
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
			
			// check whether any suboperations with need for random numbers were scheduled
//			if(operationIDs != null){
//				// only do the check when doOperations was not called from generateRandomNumbersIfNeeded() itself
//				generateRandomNumbersIfNeeded();
//			}
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


	/**
	 * Same as {@link OperationsTest#doOperation(int[])}, but simulates the loss of messages 
	 * from a privacy peer. This tests the case where a privacy peer goes offline during the
	 * computation.  
	 *
	 * @param operationIDs	the ids of the operations to execute
	 * @param failingPP the index of the failing PP
	 * @param failForAll If set to <code>true</code>, the PP fails to deliver shares to all other privacy peers. 
	 * Otherwise, each PP gets the shares with probability 50%. The later leads to inconsistent states where some 
	 * privacy peers have shares from the failing PP and others don't!
	 * @return
	 */
	protected boolean doLossyOperation(int[] operationIDs, int failingPP, boolean failForAll) {
		System.out.println("doLossyOperation will process "+primitives[0].getOperations().size()+" operations");

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

		Random rand = new Random();
		
		while(!areOperationsCompleted) {
			// send (copy) the data
			long[] dataToSend = null;
			for(int i = 0; i < numberOfPrivacyPeers; i++) {
				for(int j = 0; j < numberOfPrivacyPeers; j++) {
					if(j != i) {
						// "i sends shares to j" and "j receives shares from i"
						boolean failedTransmission = (i == failingPP) && (failForAll || rand.nextBoolean());
						if (failedTransmission) {
							dataToSend = null;
						} else {
							dataToSend = primitives[i].getDataToSend(j);
							assertNotNull("data to send is null! (from privacy peer " + i + ", in round " + roundNumber
									+ ")", dataToSend);
							assertTrue("no data to send! (from privacy peer " + i + ", in round " + roundNumber + ")",
									dataToSend.length > 0);
						}
						primitives[j].setReceivedData(i, dataToSend);
					}
				}
			}
			
			// check whether any suboperations with need for random numbers were scheduled
//			if(operationIDs != null){
//				// only do the check when doOperations was not called from generateRandomNumbersIfNeeded() itself
//				generateRandomNumbersIfNeeded();
//			}
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

	protected void initializeMpcShamirSharingInstances() {
		// Choose the default of t=(m-1)/2.
		initializeMpcShamirSharingInstances(-1);
	}

	
	
	protected void initializeMpcShamirSharingInstances(int degreeT) {
		// initialize MpcShamirSharing instances (for peers and privacy peers)
		ShamirSharing mpcShamirSharing = null;
		mpcShamirSharingPeers = new ShamirSharing[numberOfPeers];
		for(int i = 0; i < numberOfPeers; i++) {
			try{
				mpcShamirSharing = new ShamirSharing();
				mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
				mpcShamirSharing.setFieldSize(fieldSize);
				mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
				mpcShamirSharing.setDegreeT(degreeT);
				mpcShamirSharing.init();
				mpcShamirSharingPeers[i] = mpcShamirSharing;
			}
			catch (Exception e) {
				fail("An exception occured when creating and initializing a MpcShamirSharing instance: " +Utils.getStackTrace(e));
			}
		}
		mpcShamirSharing = null;
	}

	
	protected void initializeMpcShamirSharingProtocolPrimitives() {
		initializeMpcShamirSharingProtocolPrimitives(-1);
	}

	protected void initializeMpcShamirSharingProtocolPrimitives(int degreeT) {
		// initialize MpcShamirSharingProtocolPrimitives for privacy peers
		Primitives primitive = null;
		primitives = new Primitives[numberOfPrivacyPeers];
		for(int i = 0; i < numberOfPrivacyPeers; i++) {
			try{
				primitive = new Primitives(randomAlgorithm, fieldSize, degreeT, numberOfPrivacyPeers, i, 1);
			}
			catch (Exception e) {
				fail("An exception occured when creating and initializing a MpcShamirSharingProtocolPrimitives instance: " +Utils.getStackTrace(e));
			}
			primitives[i] = primitive;
		}
		primitive = null;
	}


	protected void createInputValues() {
		// create input values
		if((2*borderSize + randomValuesCount) < fieldSize) {
			input = new long[2*borderSize + randomValuesCount];
			// create lower end inputs
			for(int i = 0; i < borderSize; i++) {
				input[i] = i % fieldSize;
			}
			// create upper end inputs
			for(int i = 0; i < borderSize; i++) {
				input[borderSize+i] = (fieldSize-1 - i) % fieldSize;
			}
			// create random value inputs
			long randomValue = 0;
			for(int i = 0; i < randomValuesCount; i++) {
				randomValue = random.nextLong() % fieldSize;
				if(randomValue < 0) {
					randomValue = 0-randomValue;
				}
				input[(2*borderSize)+i] = randomValue;
			}
		} else {
			// if the amount of requested inputs is larger than the group order
			// generate all possible input values
			input = new long[(int)fieldSize];
			for(int i = 0; i < fieldSize; i++) {
				input[i] = i;
			}
		}
	}


	/**
	 * creates Shamir shares for the input values
	 */
	protected void createInputShares() {
		// create Shamir shares of input values
		inputShares = new long[numberOfPeers][numberOfPrivacyPeers][input.length];
		for(int i = 0; i < numberOfPeers; i++) {
			inputShares[i] = mpcShamirSharingPeers[i].generateShares( input );
		}
	}


	/**
	 * creates Shamir bit shares for the input values
	 */
	protected void createInputBitShares() {
		int bitsCount = primitives[0].getBitsCount();
		// create Shamir bit shares of input values
		String inputString = null;
		long[] inputBits = new long[bitsCount];
		long[][] bitShares = null;
		inputBitShares = new long[numberOfPeers][numberOfPrivacyPeers][input.length][bitsCount];
		for(int peerIndex = 0; peerIndex < numberOfPeers; peerIndex++) {
			for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
				// get bits of input
				inputString = Long.toBinaryString(input[inputIndex]);
				// create leading zeroes
				int bitIndex = 0;
				if(inputString.length() < bitsCount) {
					for(int j = inputString.length(); j < bitsCount; j++) {
						inputBits[bitIndex] = 0;
						bitIndex++;
					}
				}
				for(int j = 0; j < inputString.length(); j++) {
					if(inputString.charAt(j) == '0') {
						inputBits[bitIndex] = 0;
					} else {
						inputBits[bitIndex] = 1;
					}
					bitIndex++;
				}
				bitShares = mpcShamirSharingPeers[peerIndex].generateShares( inputBits );
				// copy bit shares into inputBitShares array
				for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
					inputBitShares[peerIndex][privacyPeerIndex][inputIndex] = new long[bitsCount];
					System.arraycopy(bitShares[privacyPeerIndex], 0, inputBitShares[peerIndex][privacyPeerIndex][inputIndex], 0, bitsCount);
				}
			}
		}
	}


	/**
	 * parses the log entries of the specified operation
	 * (replacing shares by the shared secret)
	 *
	 * @param index	the index of the operation for which to parse the logs
	 */
	protected void parseLogs(int index) {
		// next found shares: start, end, share of each privacy peer
		int[][] position = new int[operationLogs.length][2];
		long[] shares = new long[operationLogs.length];
		long result = 0;
		// while there still are shares in all logs
		while(true) {
			for(int privacyPeerIndex = 0; privacyPeerIndex < operationLogs.length; privacyPeerIndex++) {
				// extract next share from log
				position[privacyPeerIndex][0] = operationLogs[privacyPeerIndex][index].indexOf("<S>", position[privacyPeerIndex][1]);
				if(position[privacyPeerIndex][0] == -1) {
					return;
				}
				position[privacyPeerIndex][1] = operationLogs[privacyPeerIndex][index].indexOf("</S>", position[privacyPeerIndex][0]);
				shares[privacyPeerIndex] = Long.valueOf( operationLogs[privacyPeerIndex][index].substring(position[privacyPeerIndex][0]+3, position[privacyPeerIndex][1]) );
			}
			// reconstruct
			try {
				result = mpcShamirSharingPeers[0].interpolate(shares, false);
			} catch (Exception e) {
				logger.error("interpolation of shares failed: " + Utils.getStackTrace(e));
			}
			// insert result into logs
			for(int privacyPeerIndex = 0; privacyPeerIndex < operationLogs.length; privacyPeerIndex++) {
				operationLogs[privacyPeerIndex][index] = operationLogs[privacyPeerIndex][index].replaceFirst("<S>"+shares[privacyPeerIndex]+"</S>", result+" (share: "+shares[privacyPeerIndex]+")");
			}
		}
	}


	/**
	 * retrieves the logs, parses them and saves them to a file
	 *
	 * @param operationIDs	the ids of the operations of which to write the logs
	 */
	protected void writeLogs(int[] operationIDs) {
		operationLogs = new String[numberOfPrivacyPeers][operationIDs.length];
		for(int i = 0; i < operationIDs.length; i++) {
			for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				operationLogs[privacyPeerIndex][i] = primitives[privacyPeerIndex].getLog(operationIDs[i]);
			}
			parseLogs(i);
		}
		for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			String log = new String();
			for(int i = 0; i < operationLogs[privacyPeerIndex].length; i++) {
				log += "\n\n" + operationLogs[privacyPeerIndex][i];
			}
			try {
				Services.writeFile(log, "operation-logs__fs-"+fieldSize+"_pp-"+privacyPeerIndex+".txt");
			}
			catch (Exception e) {
				System.out.println("thread " + Thread.currentThread().getId() + " failed to write file: "+Utils.getStackTrace(e));
			}
		}
	}


	/**
	 * Checks whether current operations need bitwise shared random numbers and generates them if needed.
	 */
	protected void generateRandomNumbersIfNeeded() {
		long randomNumbersNeeded = 0;
		int bitsPerElement = primitives[0].getBitsCount();
		List<IOperation> ops = primitives[0].getOperations();
		
		//List<IOperation> opsWithRandomNeed = new ArrayList<IOperation>();
		for(int i=0; i<ops.size(); i++) {
			//opsWithRandomNeed.addAll(recursiveGetOperationsWithRandomNumbersNeeded(ops.get(i)));
			if (ops.get(i) instanceof LessThan) {
				randomNumbersNeeded += ((LessThan)ops.get(i)).getRandomNumbersNeeded(primitives[0]);
			}
		}
		
//		// Count random Numbers needed
//		for(int i=0; i<opsWithRandomNeed.size(); i++){
//			if (opsWithRandomNeed.get(i) instanceof LessThan) {// should always be true
//				randomNumbersNeeded += ((LessThan)opsWithRandomNeed.get(i)).getRandomNumbersNeeded(primitives[0]);
//			}
//		}

		if (randomNumbersNeeded==0)
			return;

		// Backup old operations and schedule random number generation
		for(int pp = 0; pp < numberOfPrivacyPeers; pp++) {
			primitives[pp].pushOperations();
			primitives[pp].initialize(1);
			primitives[pp].batchGenerateBitwiseRandomNumbers(0, new long[]{randomNumbersNeeded});
		}

		// Perform random number generation
		doOperation(null);

		long[][] preGeneratedRandomNumbers = new long[numberOfPrivacyPeers][];
		for(int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			preGeneratedRandomNumbers[privacyPeerIndex] = primitives[privacyPeerIndex].getResult(0);
		}
		
		// Restore old operations and add the random numbers
//		for(int pp = 0; pp < numberOfPrivacyPeers; pp++) {
//			int bitIndex=0;
//			primitives[pp].popOperations(); // restore
//			for(int op=0; op<opsWithRandomNeed.size(); op++){
//				LessThan lt = (LessThan)opsWithRandomNeed.get(op);
//				int bitsNeeded = lt.getRandomNumbersNeeded(primitives[pp])*bitsPerElement;
//				long [] bits = new long[bitsNeeded];
//				System.arraycopy(preGeneratedRandomNumbers[pp], bitIndex, bits, 0, bitsNeeded);
//				lt.setRandomNumberBitShares(bits);
//				bitIndex += bitsNeeded;
//			}
//		}
		

		// Restore old operations and add the random numbers
		for(int pp = 0; pp < numberOfPrivacyPeers; pp++) {
			int bitIndex=0;
			primitives[pp].popOperations();
			List<IOperation> ppOps = primitives[pp].getOperations();
			for(int op=0; op<ppOps.size(); op++) {
				if (ppOps.get(op) instanceof LessThan) {
					// set random bits
					LessThan lt = (LessThan)ppOps.get(op);
					int bitsNeeded = lt.getRandomNumbersNeeded(primitives[pp])*bitsPerElement;
					long[] bits = new long[bitsNeeded];
					System.arraycopy(preGeneratedRandomNumbers[pp], bitIndex, bits, 0, bitsNeeded);
					lt.setRandomNumberBitShares(bits);
					bitIndex += bitsNeeded;
				}
			}
		}	
	}
	
	protected List<IOperation> recursiveGetOperationsWithRandomNumbersNeeded(IOperation operation){
		List<IOperation> opsWithNeed = new ArrayList<IOperation>();
		//long randomNumbersNeeded = 0;
		
		if (operation instanceof LessThan) {
			opsWithNeed.add(operation);
			//randomNumbersNeeded += ((LessThan)operation).getRandomNumbersNeeded(primitives[0]);
		}
		if (operation instanceof GenericOperationState){
			IOperation[] subs = ((GenericOperationState)operation).getSubOperations();
			if(subs != null){
				for(int i = 0; i < subs.length; i++){
					opsWithNeed.addAll(recursiveGetOperationsWithRandomNumbersNeeded(subs[i]));
					//randomNumbersNeeded += ;
				}
			}
		}
		return opsWithNeed;
	}
}
