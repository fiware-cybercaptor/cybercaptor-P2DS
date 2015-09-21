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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.mpc.CountingBarrier;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.FinalResultEvent;

/**
 * A MPC privacy peer with the computation capabilities for the ADDITIVE protocol
 * 
 * @author Dilip Many
 *
 */
public class AdditivePrivacyPeer extends AdditiveBase {
    private static final Logger logger = LogManager.getLogger(AdditivePrivacyPeer.class);
    
	/** vector of protocols (between this privacy peer and the peers) */
	private Vector<AdditiveProtocolPrivacyPeerToPeer> peerProtocolThreads = null;
	/** vector of protocols (between this privacy peer and other privacy peers) */
	private Vector<AdditiveProtocolPrivacyPeerToPP> ppToPPProtocolThreads = null;
	
	/** vector of information objects for the connected peers */
	private Vector<AdditivePeerInfo> peerInfos = null;
	/** vector of information objects for the connected privacy peers */
	private Vector<AdditivePeerInfo> privacyPeerInfos = null;
	/** barrier to synchronize the peerProtocolThreads threads */
	private CountingBarrier peerProtocolBarrier = null;
	/** barrier to synchronize the ppToPPProtocolThreads threads */
	private CountingBarrier ppProtocolBarrier = null;

	/** number of input peers connected to this one */
	private int numberOfInputPeers = 0;
	/** number of initial shares that the privacy peer yet has to receive */
	private int initialSharesToReceive = 0;
	/** summed up shares */
	private long[] itemSumShares = null;
	private boolean isRoundSuccessful=true;

	/**
	 * creates a new MPC ADDITIVE privacy peer
	 *
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm 			the connection manager
	 * @throws Exception
	 */
	public AdditivePrivacyPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);

		peerInfos = new Vector<AdditivePeerInfo>();
		privacyPeerInfos = new Vector<AdditivePeerInfo>();
		peerProtocolThreads = new Vector<AdditiveProtocolPrivacyPeerToPeer>();
		ppToPPProtocolThreads = new Vector<AdditiveProtocolPrivacyPeerToPP>();
	}

	/**
	 * Initializes the privacy peer
	 */
	public void initialize() throws Exception {
		initProperties();

		currentTimeSlot = 1;
	}


	/**
	 * Initializes a new round of computation.
	 */
	public void initializeNewRound() {
		connectionManager.waitForConnections();
		connectionManager.activateTemporaryConnections();
		PrimitivesEnabledProtocol.newStatisticsRound();
		
		// Get all the active privacy peer IDs. Note that these are not necessarily all PPs configured in the config file.
		List<String> privacyPeerIDs = connectionManager.getActivePeers(true);
		List<String> inputPeerIDs = connectionManager.getActivePeers(false);
		Map<String, Integer> ppIndexMap = getIndexMap(privacyPeerIDs);
		myAlphaIndex = ppIndexMap.get(myPeerID);
		
		numberOfPrivacyPeers = privacyPeerIDs.size()+1; // Count myself
		numberOfInputPeers = inputPeerIDs.size();
		peerProtocolBarrier = new CountingBarrier(numberOfInputPeers);
		ppProtocolBarrier = new CountingBarrier(numberOfPrivacyPeers-1);
		clearPP2PPBarrier();
		
		// init counters
		initialSharesToReceive = numberOfInputPeers;
		finalResultsToDo = numberOfInputPeers;
		finalResults = null;
		isRoundSuccessful=true;
		
		primitives = new Primitives(randomAlgorithm, shamirSharesFieldOrder, degreeT, numberOfPrivacyPeers, myAlphaIndex, numberOfPrivacyPeers-1);
		createProtocolThreadsForInputPeers(inputPeerIDs);
		createProtocolThreadsForPrivacyPeers(privacyPeerIDs, ppIndexMap);
	}

	public Vector<AdditiveProtocolPrivacyPeerToPP> getPpToPPProtocolThreads() {
        return ppToPPProtocolThreads;
    }

    public Vector<AdditivePeerInfo> getPrivacyPeerInfos() {
        return privacyPeerInfos;
    }

    public long[] getItemSumShares() {
        return itemSumShares;
    }

    /**
	 * Generates a consistent mapping from active privacy peer IDs to privacy peer indices.
	 * @param connectedPrivacyPeerIDs all connected PPs, without myself.
	 * @return the index map
	 */
	private Map<String,Integer> getIndexMap(List<String> connectedPrivacyPeerIDs) {
		List<String> allPPsorted = new ArrayList<String>();
		allPPsorted.addAll(connectedPrivacyPeerIDs);
		allPPsorted.add(getMyPeerID());
		
		Collections.sort(allPPsorted);
		HashMap<String,Integer> indexMap = new HashMap<String, Integer>();
		for(int index=0; index<allPPsorted.size(); index++) {
			indexMap.put(allPPsorted.get(index), index);
		}
		return indexMap;
	}
	
	/**
	 * Create and start the threads. Attach one privacy peer id to each of them.
	 * 
	 * @param privacyPeerIDs
	 *            the ids of the privacy peers
	 * @param ppIndexMap
	 * 			  a map mapping privacy peer IDs to indices
	 */
	private void createProtocolThreadsForPrivacyPeers(List<String> privacyPeerIDs, Map<String, Integer> ppIndexMap) {
		ppToPPProtocolThreads.clear();
		privacyPeerInfos.clear();
		int currentID =0;
		for(String ppId: privacyPeerIDs) {
			logger.info("Create a thread for privacy peer " +ppId );
			int otherPPindex = ppIndexMap.get(ppId);
			AdditiveProtocolPrivacyPeerToPP pp2pp = new AdditiveProtocolPrivacyPeerToPP(currentID, this, ppId, otherPPindex, stopper);
			pp2pp.setMyPeerIndex(myAlphaIndex);
			pp2pp.addObserver(this);
			Thread thread = new Thread(pp2pp, "ADDITIVE PP-to-PP protocol connected with " + ppId);
			ppToPPProtocolThreads.add(pp2pp);
			privacyPeerInfos.add(currentID, new AdditivePeerInfo(ppId, otherPPindex));
			thread.start();
			currentID++;
		}
	}

	/**
	 * Create and start the threads. Attach one input peer id to each of them.
	 * 
	 * @param inputPeerIDs
	 *            the ids of the input peers
	 */
	private void createProtocolThreadsForInputPeers(List<String> inputPeerIDs) {
		peerProtocolThreads.clear();
		peerInfos.clear();
		int currentID = 0;
		for(String ipId: inputPeerIDs) {
			logger.info("Create a thread for input peer " +ipId );
			AdditiveProtocolPrivacyPeerToPeer pp2p = new AdditiveProtocolPrivacyPeerToPeer(currentID, this, ipId, currentID, stopper);
			pp2p.addObserver(this);
			Thread thread = new Thread(pp2p, "ADDITIVE Peer protocol connected with " + ipId);
			peerProtocolThreads.add(pp2p);
			peerInfos.add(currentID, new AdditivePeerInfo(ipId, currentID));
			thread.start();
			currentID++;
		}
	}

	/**
	 * Run the MPC protocol(s) over the given connection(s).
	 */
	public synchronized void runProtocol() {
		// All we need to do here is starting the first round
		initializeNewRound();
	}

	/**
	 * Process message received by an observable.
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	public void notificationReceived(Observable observable, Object object) throws Exception {
		if (object instanceof AdditiveMessage) {
			AdditiveMessage msg = (AdditiveMessage) object;
			// We are awaiting a message with initial shares 
			if (msg.isDummyMessage()) {
				// Counterpart is offline. Simulate an initial shares message.
				msg.setIsInitialSharesMessage(true);
			} 
			
			if (msg.isInitialSharesMessage()) {
				logger.info("Received shares from peer: " + msg.getSenderID());
				AdditivePeerInfo peerInfo = getPeerInfoByPeerID(msg.getSenderID());
				peerInfo.setInitialShares(msg.getInitialShares());

				initialSharesToReceive--;
				if (initialSharesToReceive <= 0) {
					logger.info("Received all initial shares from peers");
					startNextPPProtocolStep();
				}

			} else {
				String errorMessage = "Didn't receive initial shares";
				errorMessage += "\nisGoodBye: "+msg.isGoodbyeMessage();  
				errorMessage += "\nisHello: "+msg.isHelloMessage();
				errorMessage += "\nisInitialShares: "+msg.isInitialSharesMessage();
				errorMessage += "\nisFinalResult: "+msg.isFinalResultMessage();
				logger.error(errorMessage);
				sendExceptionEvent(this, errorMessage);
			}
		} else {
			throw new Exception("Received unexpected message type (expected: " + AdditiveMessage.class.getName() + ", received: " + object.getClass().getName());
		}
	}


	/**
	 * returns the number of peers connected to this one
	 */
	public int getNumberOfInputPeers() {
		return numberOfInputPeers;
	}


	/**
	 * returns the number of time slots
	 */
	public int getTimeSlotCount() {
		return timeSlotCount;
	}


	/**
	 * @return the numberOfItems per time slot
	 */
	public int getNumberOfItems() {
		return numberOfItems;
	}


	/**
	 * Returns the peer info for the PRIVACY PEER with the given user number, 
	 * which corresponds to the index of this privacy peer's elements in the list
	 * (null if user not in list)
	 *
	 * @param privacyPeerNumber	The privacy peer's number in the list
	 *
	 * @return The privacy peers info instance (null if not found)
	 */
	public synchronized AdditivePeerInfo getPrivacyPeerInfoByIndex(int privacyPeerNumber) {
		return privacyPeerInfos.elementAt(privacyPeerNumber);
	}


	/**
	 * Returns the peer info for the INPUT PEER with the given user number, which
	 * corresponds to the index of this privacy peer's elements in the list
	 * (null if user not in list)
	 *
	 * @param peerNumber	the peer's number in the list
	 *
	 * @return The input peers info instance (null if not found)
	 */
	private synchronized AdditivePeerInfo getPeerInfoByIndex(int peerNumber) {
		return peerInfos.elementAt(peerNumber);
	}


	/**
	 * Returns the peer info for the PEER with the given peer ID.
	 * 
	 * @param peerID	The peer's ID
	 * 
	 * @return The peers info instance (null if not found)
	 */
	private synchronized AdditivePeerInfo getPeerInfoByPeerID(String peerID) {
		for (AdditivePeerInfo peerInfo : peerInfos) {
			if (peerInfo.getID() == null) {
				logger.warn("There is a peerInfo without a peerID! " + peerInfo.getIndex());
			}
			else if (peerInfo.getID().equals(peerID)) {
				return peerInfo;
			}
		}
		return null;
	}


	/**
	 * Wait until the privacy peer is ready for the next PeerProtocol step. 
	 * @throws InterruptedException
	 */
	public void waitForNextPeerProtocolStep() {
		logger.info("PeerProtocol Barrier: Thread nr. "+(peerProtocolBarrier.getNumberOfWaitingThreads()+1)+" arrived.");
		try {
			peerProtocolBarrier.block();
		} catch (InterruptedException e) {
			// ignore
		}
	}


	/**
	 * Starts the next PeerProtocol step. 
	 * @throws InterruptedException
	 */
	public void startNextPeerProtocolStep() {
		logger.info("PeerProtocol Opening the barrier. PeerProtocol Threads can start the next step.");
		try {
			peerProtocolBarrier.openBarrier();
		} catch (InterruptedException e) {
			// ignore
		}
	}


	/**
	 * Wait until the privacy peer is ready for the next PPProtocol step. 
	 * @throws InterruptedException
	 */
	public void waitForNextPPProtocolStep() {
		logger.info("PPProtocol Barrier: Thread nr. "+(ppProtocolBarrier.getNumberOfWaitingThreads()+1)+" arrived.");
		try {
			ppProtocolBarrier.block();
		} catch (InterruptedException e) {
			// ignore
		}
	}


	/**
	 * Starts the next PPProtocol step. 
	 * @throws InterruptedException
	 */
	public void startNextPPProtocolStep() throws InterruptedException {
		logger.info("PPProtocol Opening the barrier. PPProtocol Threads can start the next step.");
		ppProtocolBarrier.openBarrier();
	}

	/**
	 * starts the reconstruction of the final result
	 */
	public void startFinalResultReconstruction() {
		int nrOfitems=itemSumShares.length;
		initializeNewOperationSet(nrOfitems);
		operationIDs = new int[nrOfitems];
		long[] data = null;
		for(int i = 0; i < nrOfitems; i++) {
			// create reconstruction operation for result of product operation
			operationIDs[i] = i;
			data = new long[1];
			data[0] = itemSumShares[i];
			if(!primitives.reconstruct(operationIDs[i], data)) {
				logger.error("reconstruct operation arguments are invalid: id="+operationIDs[i]+", data="+data[0]);
			}
		}
		logger.info("Started the final result reconstruction; (" + operationIDs.length + " reconstruction operations are in progress)");
	}


	/**
	 * retrieves and stores the final result
	 */
	public void setFinalResult() {
		logger.info("Thread " + Thread.currentThread().getId() + " called setFinalResult");
		finalResults = new long[operationIDs.length];
		for(int i = 0; i < operationIDs.length; i++) {
			finalResults[i] = primitives.getResult(operationIDs[i])[0];
		}
		logger.info("Thread " + Thread.currentThread().getId() + " starts next pp-peer protocol step");
		startNextPeerProtocolStep();
	}

	/**
	 * @return the final result
	 */
	public long[] getFinalResult() {
		return finalResults;
	}

	/**
	 * lets protocol thread report to privacy peer that it sent the final result and
	 * starts new round if there are more time slots (data) to process
	 */
	public synchronized void finalResultIsSent() {
		finalResultsToDo--;
		logger.info("thread " + Thread.currentThread().getId() + " called finalResultIsSent; finalResultsToDo="+finalResultsToDo);
		if(finalResultsToDo <= 0) {
			// report final result to observers
			logger.info( "Sent all final results. Notifying observers");
			VectorData dummy = new VectorData(); // dummy data to avoid null pointer exception in Peers::processMpcEvent
			FinalResultEvent finalResultEvent;
			finalResultEvent = new FinalResultEvent(this, myAlphaIndex, getMyPeerID(), getMyPeerID(), dummy);
			finalResultEvent.setVerificationSuccessful(true);
			sendNotification(finalResultEvent);
			// check if there are more time slots to process
			if(currentTimeSlot < timeSlotCount) {
				currentTimeSlot++;
				logger.info("thread " + Thread.currentThread().getId() + " increased currentTimeSlot to "+currentTimeSlot+", will init new round now");
				initializeNewRound();
			} else {
				logger.info("No more data available... Stopping protocol threads");
				protocolStopper.setIsStopped(true);
			}
		}
	}
	
	public boolean skipInputVerification() {
		return skipInputVerification;
	}
	
	/**
	 * Add up all the shares received (of non-disqualified users).
	 */
	public void addShares() {
		itemSumShares = new long[numberOfItems];
		ShamirSharing ss = primitives.getMpcShamirSharing();
		for (AdditivePeerInfo peerInfo : peerInfos) {
			if (peerInfo.isVerificationSuccessful()) {
				logger.info("Adding share of: " + peerInfo.getID());
				itemSumShares = ss.vectorAdd(itemSumShares, peerInfo.getInitialShares());
			} else {
				logger.warn("Peer is disqualified: " + peerInfo.getID() + " -> Not adding share");
			}
		}
	}

	/**
	 * starts the less-thans of the max. element check
	 */
	public void startLessThans() {

		// create less-than operation set
		initializeNewOperationSet(numberOfInputPeers*numberOfItems);
		operationIDs = new int[numberOfInputPeers*numberOfItems];
		int operationIndex = 0;
		int dataSize = 5;
		long[] data = null;
		long isLessThanHalfOfFieldSize = (maxElement <= (shamirSharesFieldOrder/2)) ? 1 : 0;
		for(int peerIndex = 0; peerIndex < numberOfInputPeers; peerIndex++) {
			for(int itemIndex = 0; itemIndex < numberOfItems; itemIndex++) {
				operationIDs[operationIndex] = operationIndex;
				data = new long[dataSize];
				data[0] = getPeerInfoByIndex(peerIndex).getInitialShares()[itemIndex];
				data[1] = maxElement;
				data[2] = -1;
				data[3] = isLessThanHalfOfFieldSize;
				data[4] = -1;
				if(!primitives.lessThan(operationIndex, data)) {
					Services.printVector("less-than operation arguments are invalid: id="+peerIndex+"; data=", data, logger);
				}
				operationIndex++;
			}
		}
		logger.info("thread " + Thread.currentThread().getId() + " started the less-thans of the max. element check; (" + operationIDs.length + " less-than operations are in progress)");
	}


	/**
	 * gets the results of the less-than operations of the norm bound check,
	 * and reconstructs the result
	 */
	public void startNormBoundCheckResultReconstruction() {
		long[] result = new long[operationIDs.length];
		for(int i = 0; i < operationIDs.length; i++) {
			// get less-than operation result
			result[i] = primitives.getResult(operationIDs[i])[0];
		}

		initializeNewOperationSet(result.length);
		operationIDs = new int[result.length];
		long[] data = null;
		for(int i = 0; i < result.length; i++) {
			// create reconstruction operation for result of less-than operation
			operationIDs[i] = i;
			data = new long[1];
			data[0] = result[i];
			if(!primitives.reconstruct(operationIDs[i], data)) {
				logger.error("reconstruct operation arguments are invalid: id="+operationIDs[i]+", data="+data[0]);
			}
		}
		logger.info("thread " + Thread.currentThread().getId() + " started the norm bound check result reconstruction; (" + operationIDs.length + " reconstruction operations are in progress)");
	}


	/**
	 * gets the results of the less-than operations of the weights threshold check,
	 * and reconstructs the result
	 */
	public void processNormBoundCheckResult() {
		isRoundSuccessful = true;
		int numberOfDishonestPeers = 0;
		int operationIndex = 0;
		for(int peerIndex = 0; peerIndex < numberOfInputPeers; peerIndex++) {
			for(int itemIndex = 0; itemIndex < numberOfItems; itemIndex++) {
				operationIndex = peerIndex*numberOfItems +itemIndex;
				if(primitives.getResult(operationIndex)[0] != 1) {
					getPeerInfoByIndex(peerIndex).setVerificationSuccessful(false);
					numberOfDishonestPeers++;
					logger.warn("Input peer " + getPeerInfoByIndex(peerIndex).getID() + " was disqualified! It shared a value bigger than "+maxElement+"!");
					break;
				} else {
					getPeerInfoByIndex(peerIndex).setVerificationSuccessful(true);
				}
			}
		}

		int numberOfHonestPeers = numberOfInputPeers - numberOfDishonestPeers;
		if(numberOfHonestPeers < numberOfInputPeers) {
            logger.error("Not enough honest peers ("+numberOfHonestPeers+" < "+numberOfInputPeers+")! Sending verification results.");
            isRoundSuccessful = false;
        }
		logger.info("Processed the norm bound check results");
	}
	
	public boolean isRoundSuccessful() {
		return isRoundSuccessful;
	}

}
