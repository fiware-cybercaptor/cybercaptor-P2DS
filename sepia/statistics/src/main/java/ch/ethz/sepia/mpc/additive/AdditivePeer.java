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

package ch.ethz.sepia.mpc.additive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.FinalResultEvent;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.DirectoryPoller;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * A MPC peer providing the private input data for the ADDITIVE protocol
 *
 * @author Dilip Many
 *
 */
public class AdditivePeer extends AdditiveBase {
    private static final Logger logger = LogManager.getLogger(AdditivePeer.class);
    
	/** vector of protocols (between this peer and the privacy peers) */
	protected Vector<AdditiveProtocolPeer> peerProtocolThreads = null;
	/** MpcShamirSharing instance to use basic operations on Shamir shares */
	protected ShamirSharing mpcShamirSharing = null;

	/** indicates if the initial shares were generated yet */
	protected boolean initialSharesGenerated = false;
	/** array containing my initial shares; dimensions: [numberOfPrivacyPeers][numberOfItems] */
	protected long[][] initialShares = null;

	protected DirectoryPoller poller;
	protected VectorData data;
	
	/**
	 * constructs a new ADDITIVE peer object
	 *
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm the connection manager
	 * @throws Exception
	 */
	public AdditivePeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);
		peerProtocolThreads = new Vector<AdditiveProtocolPeer>();
		mpcShamirSharing = new ShamirSharing();
	}

	/**
	 * Initializes the peer
	 */
	public void initialize() throws Exception {
		initProperties();

		mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
		mpcShamirSharing.setFieldSize(shamirSharesFieldOrder);
		if (degreeT>0) {
			mpcShamirSharing.setDegreeT(degreeT);
		}
			
		currentTimeSlot = 1;
		
    	// Create output folder if it does not exist
        File folder = new File(outputFolder);
    	if (!folder.exists()) {
    		folder.mkdir();
    	}

    	// Init the input directory poller
   		poller = new DirectoryPoller(stopper, new File(inputFolder));
   		poller.setTimeout(inputTimeout);

	}

	/**
	 * Initializes and starts a new round of computation. It first (re-)established connections and
	 * then creates and runs the protocol threads for the new round. 
	 */
	protected void initializeNewRound() {
		connectionManager.waitForConnections();
		connectionManager.activateTemporaryConnections();
		PrimitivesEnabledProtocol.newStatisticsRound();
		
		List<String> privacyPeerIDs = connectionManager.getActivePeers(true);
		Collections.sort(privacyPeerIDs);
		numberOfPrivacyPeers = privacyPeerIDs.size();
		mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
		mpcShamirSharing.init();
		clearPP2PPBarrier();
		
		// Init state variables
		initialSharesGenerated = false;
		initialShares = null;
		finalResults = null;
		finalResultsToDo = numberOfPrivacyPeers;

		try {
			readDataFromStdin();
		} catch (IOException e) {
			logger.error(Utils.getStackTrace(e));
			return;
		}
		createProtocolThreadsForPrivacyPeers(privacyPeerIDs);
	}

	
	/**
	 * Create and start the threads. Attach one privacy peer id to each of them.
	 * 
	 * @param privacyPeerIDs the ids of the privacy peers
	 */
	protected void createProtocolThreadsForPrivacyPeers(List<String> privacyPeerIDs)  { 
		peerProtocolThreads.clear();
		int currentID = 0;
		for(String ppId: privacyPeerIDs) {
			logger.info("Create a thread for privacy peer " +ppId );
			AdditiveProtocolPeer AdditiveProtocolPeer = new AdditiveProtocolPeer(currentID, this, ppId, currentID, stopper);
			AdditiveProtocolPeer.addObserver(this);
			Thread thread = new Thread(AdditiveProtocolPeer, "ADDITIVE Peer protocol with user number " + currentID);
			peerProtocolThreads.add(AdditiveProtocolPeer);
			thread.start();
			currentID++;
		}
	}

	/**
	 * Generates shares for each secret input.
	 */
	public synchronized void generateInitialShares() {
		if(!initialSharesGenerated) {
			initialSharesGenerated = true;
			logger.info("Generating initial shares");
			initialShares = mpcShamirSharing.generateShares(data.getInput());
			logger.info("DONE generating initial shares");
		}
	}


	/**
	 * Returns the initial shares for the privacy peer.
	 *
	 * @param privacyPeerIndex	index of privacy peer for which to return the initial shares
	 */
	protected long[] getInitialSharesForPrivacyPeer(int privacyPeerIndex) {
		return initialShares[privacyPeerIndex];
	}


	/**
	 * Run the MPC protocol(s) over the given connection(s).
	 */
	public void runProtocol() throws Exception {
		// All we need to do here is starting the first round
		initializeNewRound();
	}


	/**
	 * Process message received by an observable.
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	protected void notificationReceived(Observable observable, Object object) throws Exception {
		if (object instanceof AdditiveMessage) {
			// We are awaiting a final results message				
			AdditiveMessage additiveMessage = (AdditiveMessage) object;

			if(additiveMessage.isDummyMessage()) {
				// Simulate a final results message in order not to stop protocol execution
				additiveMessage.setIsFinalResultMessage(true);
			}

			if(additiveMessage.isFinalResultMessage()) {
				logger.info("Received a final result message from a privacy peer");
				finalResultsToDo--;

				if (finalResults == null && additiveMessage.getResults() != null) {
					finalResults = additiveMessage.getResults();
					data.setOutput(finalResults);
				}

				if(finalResultsToDo <= 0) {
					// notify observers about final result
					logger.info( "Received all final results. Notifying observers");
					VectorData dummy = new VectorData(); // dummy data to avoid null pointer exception in Peers::processMpcEvent
					FinalResultEvent finalResultEvent = new FinalResultEvent(this, myAlphaIndex, getMyPeerID(), additiveMessage.getSenderID(), dummy);
					finalResultEvent.setVerificationSuccessful(true);
					sendNotification(finalResultEvent);

					// check for disqualification
					if(data.getOutput().length != numberOfItems) {
						logger.warn("Computation FAILED: too many input peers were disqualified!"); // therefore received result is input verification result (size = numberOfInputPeers * numberOfItems)
					}

					writeDataToStdout();

					// check if there are more time slots to process
					if(currentTimeSlot < timeSlotCount) {
						currentTimeSlot++;
						initializeNewRound();
					} else {
						logger.info("No more data available... Stopping protocol threads");
						protocolStopper.setIsStopped(true);
					}
				}
			} else {
				String errorMessage = "Didn't receive final result; last message is " + additiveMessage;
				logger.error(errorMessage);
				sendExceptionEvent(this, errorMessage);
			}
		} else {
			throw new Exception("Received unexpected message type (expected: " + AdditiveMessage.class.getName() + ", received: " + object.getClass().getName());
		}
	}
	
    /**
     * Polls the input directory for new files and reads the next available file.
     * @throws IOException 
     */
    protected void readNextInputFile() throws IOException  {
    	File nextFile = poller.getNextFile();
    	if (nextFile != null) {
    		data = new VectorData();
    		data.readDataFromFile(nextFile);
    	} else {
            logger.error("Missing next input file! (timeout occured)");
    	}
    }
    
    /**
     * Polls input data for the next round from stdin. This is done by reading
     * a single line from stdin which shall be a series of longs with ',' as the delimiter. 
     * @throws IOException
     */
    protected void readDataFromStdin() throws IOException {
    	 BufferedReader in
    	   = new BufferedReader(new InputStreamReader(System.in));
    	 String line = in.readLine();
    	 
    	 data = new VectorData();
    	 
    	 String[] parts = line.split(",");
    	 long[] input = new long[parts.length];
    	 
    	 /* munt: From studying the code of VectorData.readDataFromFile it appears
    	  * that it will also create an array of longs for the output. Which is why
    	  * we do the same thing here.
    	  */
    	 long[] output = new long[parts.length];
    	 
    	 
    	 for(int i = 0; i < parts.length; i++) {
    		 try {
    			 input[i] = Long.parseLong(parts[i]);
    		 }
    		 catch(Exception ex) {
    			 logger.catching(ex);
    			 logger.error("Reading input from stdin failed!");
    			 
    			 throw new IOException(ex); //re-throw it
    		 }
    	 }
    	 
    	 data.setInput(input);
    	 data.setOutput(output);
    }

    /**
     * Write the output to a file.
     * @throws FileNotFoundException
     */
    protected void writeOutputToFile() throws FileNotFoundException {
		String fileName = outputFolder + "/" + OUTPUT_FILE_PREFIX
				+ String.valueOf(getMyPeerID()).replace(":", "_") + "_round"
				+ currentTimeSlot + ".csv";
		data.writeOutputToFile(new File(fileName));
    }
    
    /**
     * Write the output to stdout
     * @throws IOException
     */
    protected void writeDataToStdout() throws IOException {
    	long[] output = data.getOutput();
    	StringBuilder sb = new StringBuilder();
    	for(int i = 0; i < output.length - 1; i++) {
    		sb.append(output[i]);
    		sb.append(", ");
    	}
    	if(output.length > 0)
    		sb.append(output[output.length -1 ]);
    	System.out.println(sb.toString());
    	System.out.flush(); // flush it.
    }
	
}
