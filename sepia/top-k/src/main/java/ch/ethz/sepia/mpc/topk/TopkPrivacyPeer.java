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

package ch.ethz.sepia.mpc.topk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Properties;
import java.util.Vector;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.FinalResultEvent;
import ch.ethz.sepia.mpc.CountingBarrier;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;

/**
 * A MPC privacy peer with the computation capabilities for the topk protocol
 *
 * @author Dilip Many
 *
 */
public class TopkPrivacyPeer extends TopkBase {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(TopkPrivacyPeer.class));

	/** vector of protocols (between this privacy peer and the peers) */
	private Vector<TopkProtocolPrivacyPeerToPeer> peerProtocolThreads = null;
	/** vector of protocols (between this privacy peer and other privacy peers) */
	private Vector<TopkProtocolPrivacyPeerToPP> ppToPPProtocolThreads = null;
	/** vector of information objects for the connected peers */
	protected Vector<TopkPeerInfo> peerInfos = null;
	/** vector of information objects for the connected privacy peers */
	protected Vector<TopkPeerInfo> privacyPeerInfos = null;
	/** barrier to synchronize the peerProtocolThreads threads */
	private CountingBarrier peerProtocolBarrier = null;
	/** barrier to synchronize the ppToPPProtocolThreads threads */
	private CountingBarrier ppProtocolBarrier = null;

	/** number of input peers connected to this one */
	protected int numberOfInputPeers = 0;
	/** number of initial shares that the privacy peer yet has to receive */
	private int initialSharesToReceive = 0;

	private boolean keysAreIpAddresses = false;

	private long[][] aggregateValueShares; // [S][H]

	// variables for binary search for tau ([S])
	private long[] tau;  // current threshold
	private long[] oldtau;  // threshold of previous round
	private long[] ubound; // upper bound
	private long[] lbound; // lower bound
	private boolean[] bsFinished; // is the binary search finished?
	private long[] biggercountShares; // [S]

	/** Shares of lessThans in binary search */
	protected long[][] lessThanShares;
	/** Reconstructed lessThans from binary search */
	protected boolean[][] lessThans;
	/** Shares of equals for collision resolution */
	protected long[][][][] equalShares;
	/** Shares of aggregate values per key for collision resolution */
	protected long[][][] aggrValuesPerKeyShares;

	/** Shares of the key with maximum aggregate value in each slot [S][H] */
	protected long[][] maxKeyPerSlotShares;
	/** Shares of the maximum aggregate value in each slot [S][H] */
	protected long[][] maxValuePerSlotShares;

	/** The number of slots that need collision resolution */
	int collidingSlotCount;

	public static final String PROP_TOPK_MAXTAU = "mpc.topk.maxtau"; // maximum value for tau
	/** The maximum tau to expect. This is used as the initial upper bound of the binary search for the
	 * value separating the k-th from the (k+1)-th value. */
	protected long maxTau;


	/**
	 * creates a new MPC topk privacy peer
	 *
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm 			the connection manager
	 * @throws Exception
	 */
	public TopkPrivacyPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);

		peerInfos = new Vector<TopkPeerInfo>();
		privacyPeerInfos = new Vector<TopkPeerInfo>();
		peerProtocolThreads = new Vector<TopkProtocolPrivacyPeerToPeer>();
		ppToPPProtocolThreads = new Vector<TopkProtocolPrivacyPeerToPP>();
	}

	protected synchronized void initProperties() throws Exception {
		super.initProperties();

        // TODO: What is the right "peerName" here?
        Properties properties = Configuration.getInstance(makePeerName(getMyPeerIndex())).getProperties();

		// Set properties specific to top-k privacy peers
        maxTau = Integer.valueOf(properties.getProperty(PROP_TOPK_MAXTAU));
        logger.info("Top-k parameter maxTau="+maxTau);
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
	protected void initializeNewRound() {
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
		aggregateValueShares = null;

		primitives = new Primitives(randomAlgorithm, shamirSharesFieldOrder, degreeT, numberOfPrivacyPeers, myAlphaIndex, numberOfPrivacyPeers-1);
		createProtocolThreadsForInputPeers(inputPeerIDs);
		createProtocolThreadsForPrivacyPeers(privacyPeerIDs, ppIndexMap);
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
			TopkProtocolPrivacyPeerToPP pp2pp = new TopkProtocolPrivacyPeerToPP(currentID, this, ppId, otherPPindex, stopper);
			pp2pp.setMyPeerIndex(myAlphaIndex);
			pp2pp.addObserver(this);
			Thread thread = new Thread(pp2pp, "Topk PP-to-PP protocol connected with " + ppId);
			ppToPPProtocolThreads.add(pp2pp);
			privacyPeerInfos.add(currentID, new TopkPeerInfo(ppId, otherPPindex));
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
			TopkProtocolPrivacyPeerToPeer pp2p = new TopkProtocolPrivacyPeerToPeer(currentID, this, ipId, currentID, stopper);
			pp2p.addObserver(this);
			Thread thread = new Thread(pp2p, "Topk Peer protocol connected with " + ipId);
			peerProtocolThreads.add(pp2p);
			peerInfos.add(currentID, new TopkPeerInfo(ipId, currentID));
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
	protected void notificationReceived(Observable observable, Object object) throws Exception {
		if (object instanceof TopkMessage) {
			TopkMessage msg = (TopkMessage) object;
			// We are awaiting a message with initial shares
			if (msg.isDummyMessage()) {
				// Counterpart is offline. Simulate an initial shares message.
				msg.setIsInitialSharesMessage(true);
			}

			if (msg.isInitialSharesMessage()) {
				logger.info("Received shares from peer: " + msg.getSenderID());
				TopkPeerInfo peerInfo = getPeerInfoByPeerID(msg.getSenderID());
				peerInfo.setInitialKeyShares(msg.getInitialKeyShares());
				peerInfo.setInitialValueShares(msg.getInitialValueShares());
				peerInfo.setInitialSharesReceived(true);
				keysAreIpAddresses = msg.areKeysIpAddresses();

				initialSharesToReceive--;
				if (initialSharesToReceive <= 0) {
					logger.info("Received all initial shares from peers...");
					startNextPPProtocolStep();
				}

			} else {
				String errorMessage = "Didn't receive initial shares...";
				errorMessage += "\nisGoodBye: "+msg.isGoodbyeMessage();
				errorMessage += "\nisHello: "+msg.isHelloMessage();
				errorMessage += "\nisInitialShares: "+msg.isInitialSharesMessage();
				errorMessage += "\nisFinalResult: "+msg.isFinalResultMessage();
				logger.error(errorMessage);
				sendExceptionEvent(this, errorMessage);
			}
		} else {
			throw new Exception("Received unexpected message type (expected: " + TopkMessage.class.getName() + ", received: " + object.getClass().getName());
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
	protected synchronized TopkPeerInfo getPrivacyPeerInfoByIndex(int privacyPeerNumber) {
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
	protected synchronized TopkPeerInfo getPeerInfoByIndex(int peerNumber) {
		return peerInfos.elementAt(peerNumber);
	}


	/**
	 * Returns the peer info for the PEER with the given peer ID.
	 *
	 * @param peerID	The peer's ID
	 *
	 * @return The peers info instance (null if not found)
	 */
	protected synchronized TopkPeerInfo getPeerInfoByPeerID(String peerID) {
		for (TopkPeerInfo peerInfo : peerInfos) {
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
	protected void startNextPeerProtocolStep() {
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
	protected void startNextPPProtocolStep() throws InterruptedException {
		logger.info("PPProtocol Opening the barrier. PPProtocol Threads can start the next step.");
		ppProtocolBarrier.openBarrier();
	}


	public void setFinalResult() throws InterruptedException {
		ArrayList<HashMap<Long, Long>> mapList = new ArrayList<HashMap<Long, Long>>();
		HashSet<Long> allKeys = new HashSet<Long>();

		// Read reconstructed results
		int opCount=0;
		for(int s=0; s<S; s++) {
			int collidingSlotCountThisArray=0;
			for(int element=0; element<H; element++) {
				if (!lessThans[s][element] && collidingSlotCountThisArray<K) {
					collidingSlotCountThisArray++;
				}
			}
			HashMap<Long,Long> map = new HashMap<Long,Long>(K);
			mapList.add(map);
			for(int k=0; k<collidingSlotCountThisArray; k++) {
				long key = primitives.getResult(opCount++)[0];
				long aggrValue = primitives.getResult(opCount++)[0];
				map.put(key, aggrValue);
				allKeys.add(key);
			}
		}

		StringBuilder sb = new StringBuilder(Services.getFilterPassingLogPrefix());
		for(int s=0; s<S; s++) {
			sb.append("\nHash array #"+s+":\n");
			sb.append(mapList.get(s).toString());
		}

		// Choose the maximum for each key over all hash arrays
		HashMap<Long, Long> maxMap = new HashMap<Long, Long>();
		for(Long key:allKeys) {
			for(int s=0; s<S; s++) {
				long existingValue = maxMap.containsKey(key) ? maxMap.get(key) : 0;
				long newValue = mapList.get(s).containsKey(key) ? mapList.get(s).get(key) : 0;
				maxMap.put(key, Math.max(newValue, existingValue));
			}
		}

		sb.append("\nMax Map:\n"+maxMap.toString());
		LinkedHashMap<Long, Long> sortedMap = sortByValueDesc(maxMap);
		LinkedHashMap<Long, Long> finalMap = new LinkedHashMap<Long,Long>();

		// Select the top K
		sb.append("\nFinal results:\n");
		int count=0;
		for(Entry<Long, Long> entry:sortedMap.entrySet()) {
			if (count++ < K) {
				finalMap.put(entry.getKey(), entry.getValue());

				if (keysAreIpAddresses) {
					sb.append("("+renderIPV4Address(entry.getKey())+", "+entry.getValue()+"), ");
				} else {
					sb.append("("+entry.getKey()+", "+entry.getValue()+"), ");
				}
			}
		}

		finalResults = finalMap;
		logger.info(sb.toString());

		startNextPeerProtocolStep();

	}
	/**
	 * lets protocol thread report to privacy peer that it sent the final result and
	 * starts new round if there are more time slots (data) to process
	 */
	protected synchronized void finalResultIsSent() {
		finalResultsToDo--;
		logger.info("thread " + Thread.currentThread().getId() + " called finalResultIsSent; finalResultsToDo="+finalResultsToDo);
		if(finalResultsToDo <= 0) {
			// report final result to observers
			logger.info(Services.getFilterPassingLogPrefix()+ "Sent all final results. Notifying observers...");
			VectorData dummy = new VectorData(); // dummy data to avoid null pointer exception in Peers::processMpcEvent
			FinalResultEvent finalResultEvent;
			finalResultEvent = new FinalResultEvent(this, myAlphaIndex, getMyPeerID(), getMyPeerID(), dummy);
			finalResultEvent.setVerificationSuccessful(true);
			sendNotification(finalResultEvent);
			// check if there are more time slots to process
			if(currentTimeSlot < timeSlotCount) {
				currentTimeSlot++;
				logger.info("thread " + Thread.currentThread().getId() + " increased currentTimeSlot to "+currentTimeSlot+", will init new round now...");
				initializeNewRound();
			} else {
				logger.info("No more data available... Stopping protocol threads...");
				protocolStopper.stop();
			}
		}
	}

	/**
	 * Sorts a map by its values, in descending order.
	 * @param map the unsorted map.
	 * @return the sorted map.
	 */
	private static LinkedHashMap<Long, Long> sortByValueDesc(Map<Long, Long> map) {
		List<Map.Entry<Long, Long>> list = new LinkedList<Map.Entry<Long, Long>>(map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<Long, Long>>() {
			public int compare(Map.Entry<Long, Long> o1, Map.Entry<Long, Long> o2) {
				return (-1)*(o1.getValue().compareTo(o2.getValue()));
			}
		});

		LinkedHashMap<Long, Long> result = new LinkedHashMap<>();
		for (Map.Entry<Long, Long> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}


	public HashMap<Long, Long> getFinalResults() {
		return finalResults;
	}

	/**
	 * Aggregates the value vectors submitted by the input peers.
	 */
	public void aggregateValues() {
		aggregateValueShares = new long[S][H];
		for(int s=0; s<S; s++) {
			for(int ip=0; ip<numberOfInputPeers; ip++) {
				long[][] valueShares = getPeerInfoByIndex(ip).getInitialValueShares();
				for (int pos=0; pos<H; pos++) {
					aggregateValueShares[s][pos] = primitives.getMpcShamirSharing().modAdd(aggregateValueShares[s][pos], valueShares[s][pos]);
				}
			}
		}

	}

	/**
	 * Resets all the variables needed for the binary search.
	 */
	public void initBinarySearch() {
		bsFinished = new boolean[S];
		lbound = new long[S];
		ubound = new long[S];
		tau = new long[S];
		lessThanShares = new long[S][H];
		for(int s=0; s<S; s++) {
			bsFinished[s] = false;
			lbound[s] = 0;
			ubound[s] = maxTau;
			if (oldtau==null) {
				// maxTau must be big enough to ensure correctness.
				// But initial tau can be smaller than maxTau/2 for faster search conversion
				tau[s] = Math.round(maxTau/10.0);
			} else {
				// Use old tau as prediction for next round
				tau[s] = oldtau[s]*2;
			}
		}
		if(oldtau==null) {
			oldtau = new long[S];
		}

		primitives.getPredicateCache().clear();
	}

	/**
	 * The binary search is finished, if for all hash arrays, a suitable tau has been found.
	 * @return true if search is over.
	 */
	public boolean isBinarySearchFinished() {
		boolean finished = true;
		for(int s=0; s<S; s++) {
			finished &= bsFinished[s];
		}
		return finished;
	}


	/**
	 * Schedules comparisons for a round of binary search.
	 */
	public void scheduleBinarySearchComparisons() {
		int operationCount = getNumberOfOpenSearches()*H;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];
		int opCount=0;

		for(int s=0; s<S; s++) {
			if (!bsFinished[s]) {
				for(int elementCount=0; elementCount<H; elementCount++) {
					operationIDs[opCount]=opCount;

					long[] data = new long[5];
					data[0] = aggregateValueShares[s][elementCount];
					data[1] = tau[s];
					data[2] = -1;
					data[3] = (tau[s]<=primitives.getFieldSize()/2.0) ? 1 : 0;
					data[4] = -1;

					String keyA = "aggr-s+"+s+"e"+elementCount;
					String keyB = null; // no secret value
					String keyAB = null; // each value is only compared once to each tau
					primitives.lessThan(opCount++, data, keyA, keyB, keyAB);
				}
			}
		}

	}

	/**
	 * Returns the number of binary searches that are not finished yet.
	 * @return number of open binary searches.
	 */
	private int getNumberOfOpenSearches() {
		int nrOfSearches = 0;
		for(int s=0; s<S; s++) {
			nrOfSearches += bsFinished[s]?0:1;
		}
		return nrOfSearches;
	}

	/**
	 * Processes the comparison results and schedules comparisons of the number of values
	 * above tau (biggercount). This is not very efficient as only a small number of lessThans
	 * are performed in parallel.
	 */
	public void scheduleBinarySearchBiggercount() {
		biggercountShares = new long[S];

		ShamirSharing ss = primitives.getMpcShamirSharing();
		int opCount=0;
		int failCount=0;

		// Retrieve the lessThans
		for(int s=0; s<S; s++) {
			if (!bsFinished[s]) {
				for(int elementCount=0; elementCount<H; elementCount++) {
					lessThanShares[s][elementCount] = primitives.getResult(opCount++)[0];
					if (lessThanShares[s][elementCount] == -1) {
						failCount++;
					} else {
						biggercountShares[s] = ss.modAdd(biggercountShares[s], ss.modSubtract(1, lessThanShares[s][elementCount]));
					}
				}
			}
		}
		if (failCount>0) {
			logger.error("+++: LessThan failed for "+failCount+" of "+opCount+" operations.");
		}

		// Schedule comparison of [biggercount]<K and [biggercount]<K+1
		int operationCount = getNumberOfOpenSearches()*2;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];
		opCount=0;

		for(int s=0; s<S; s++) {
			if (!bsFinished[s]) {
				operationIDs[opCount]=opCount;

				primitives.smallIntervalTest(opCount++, new long[]{biggercountShares[s], 0, K-1});
				primitives.equal(opCount++, new long[]{biggercountShares[s], K});
			}
		}

	}

	/**
	 * Schedules the reconstruction of [biggercount]<K and [biggercount]<K+1.
	 */
	public void scheduleBinarySearchBiggercountReconstruction() {
		long[] bcLessThanKShares = new long[S];
		long[] bcEqualsKShares = new long[S];

		int opCount=0;
		for(int s=0; s<S; s++) {
			if (!bsFinished[s]) {
				bcLessThanKShares[s] = primitives.getResult(opCount++)[0];
				bcEqualsKShares[s] = primitives.getResult(opCount++)[0];
			}
		}

		// Schedules reconstruction
		int operationCount = 2*getNumberOfOpenSearches();
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];

		opCount=0;
		for(int s=0; s<S; s++) {
			if (!bsFinished[s]) {
				operationIDs[opCount]=opCount;
				primitives.reconstruct(opCount++, new long[]{bcLessThanKShares[s]});
				operationIDs[opCount]=opCount;
				primitives.reconstruct(opCount++, new long[]{bcEqualsKShares[s]});
			}
		}

	}


	/**
	 * Checks if the correct threshold tau was found. Adapts the upper and lower bounds if not finished.
	 */
	public void binarySearchDecision() {
		boolean[] bcLessThanK = new boolean[S];
		boolean[] bcEqualsK = new boolean[S];
		int opCount=0;
		StringBuilder sb = new StringBuilder("+++: Finding Binary Search Decision...\n");
		for(int s=0; s<S; s++) {
			if (!bsFinished[s]) {
				bcLessThanK[s] = primitives.getResult(opCount++)[0]==1;
				bcEqualsK[s] = primitives.getResult(opCount++)[0]==1;
			}

			sb.append("S="+s+", Tau="+tau[s]+", bc<K: "+bcLessThanK[s] + " bc==K: "+bcEqualsK[s]+"\n");

			if(!bsFinished[s]) {
				// Adapt bounds
				if (bcEqualsK[s]) {
					bsFinished[s] = true;
				} else {
					if (bcLessThanK[s]) {
						// Tau is too high, lower it
						ubound[s] = tau[s];
					} else {
						// Tau is too low, raise it
						lbound[s] = tau[s];
					}
					long oldTau = tau[s];
					tau[s] = Math.round((lbound[s]+ubound[s])/2.0);

					if(tau[s]==oldTau) {
						// There are some items with the same values. Stop search.
						bsFinished[s] = true;
						sb.append("Binary search STOPPED beacuse new Tau equals old Tau!\n");
					}
				}
			}
			if (bsFinished[s]) {
				sb.append("Binary search FINISHED. Tau="+tau[s]+", bounds=["+lbound[s]+", "+ubound[s]+"]\n");
				oldtau[s] = tau[s];
			}
			else
				sb.append("New Parameters: Tau="+tau[s]+", bounds=["+lbound[s]+", "+ubound[s]+"]\n");

		}
		logger.info(sb.toString());

	}

	/**
	 * Schedules the reconstruction of lessThans with correct Tau.
	 */
	public void scheduleLessThanReconstruction() {

		// Schedules reconstruction
		int operationCount = S*H;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];

		int opCount=0;
		for(int s=0; s<S; s++) {
			for(int elementCount=0; elementCount<H; elementCount++) {
				operationIDs[opCount]=opCount;
				primitives.reconstruct(opCount++, new long[]{lessThanShares[s][elementCount]});
			}
		}

	}

	/**
	 * Stores the reconstructed lessThans for further use.
	 */
	public void retrieveLessThans () {
		lessThans = new boolean[S][H];

		int opCount=0;
		for(int s=0; s<S; s++) {
			for(int elementCount=0; elementCount<H; elementCount++) {
				lessThans[s][elementCount] = primitives.getResult(opCount++)[0]==1;
			}
		}
	}

	/**
	 * Schedules the equal operations for the global collision resolution.
	 */
	public void scheduleCollisionResolutionEquals() {
		// Indices of top-k items are stored in lessThans.
		// Count the number of slots that need collision resolution. Should be S*K, but in case
		// there are keys with equal values, it can also be slightly more or less.
		collidingSlotCount=0;
		for(int s=0; s<S; s++) {
			for(int element=0; element<H; element++) {
				if (!lessThans[s][element]) {
					collidingSlotCount++;
				}
			}
		}

		// For each slot, we need n(n-1)/2 equals, where n is the number of input peers.
		int equalsPerSlot = (numberOfInputPeers*(numberOfInputPeers-1))/2;
		logger.info("+++CR: We have "+collidingSlotCount+" slots that need collision resolution. Each needs "+equalsPerSlot+" equals.");

		int operationCount = collidingSlotCount * equalsPerSlot;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];

		int opCount=0;
		for(int s=0; s<S; s++) {
			for(int element=0; element<H; element++) {
				if(!lessThans[s][element]) {
					// This slots needs collision resolution. Compare the keys of all input peers with each other.
					for(int ip1=0; ip1<numberOfInputPeers; ip1++) {
						long keyShareIp1 = getPeerInfoByIndex(ip1).getInitialKeyShares()[s][element];
						for(int ip2=ip1+1; ip2<numberOfInputPeers; ip2++) {
							long keyShareIp2 = getPeerInfoByIndex(ip2).getInitialKeyShares()[s][element];
							operationIDs[opCount]=opCount;
							primitives.equal(opCount++, new long[]{keyShareIp1, keyShareIp2});
						}
					}
				}
			}
		}

	}

	/**
	 * Retrieves the equal operations for the global collision resolution.
	 */
	public void retrieveCollisionResolutionEquals() {
		equalShares = new long[S][H][numberOfInputPeers][numberOfInputPeers];

		int opCount=0;
		for(int s=0; s<S; s++) {
			for(int element=0; element<H; element++) {
				if(!lessThans[s][element]) {
					for(int ip1=0; ip1<numberOfInputPeers; ip1++) {
						for(int ip2=ip1+1; ip2<numberOfInputPeers; ip2++) {
							equalShares[s][element][ip1][ip2] = primitives.getResult(opCount++)[0];
						}
					}
				}
			}
		}

	}

	/**
	 * For the value aggregation, we compute two values for each key pair:
	 * [k1==k2]*v1 and [k1==k2]*v2. From these values, the aggregation can be
	 * performed using addition only (see {@link #retrieveCollisionResolutionValueAggregation()}).
	 */
	public void scheduleCollisionResolutionValueAggregation() {
		int nrOfPairs = (numberOfInputPeers*(numberOfInputPeers-1))/2;
		int operationCount = 2*collidingSlotCount*nrOfPairs;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];
		int opCount=0;

		// schedule value aggregation
		for(int s=0; s<S; s++) {
			for(int element=0; element<H; element++) {
				if(!lessThans[s][element]) {
					for(int ip1=0; ip1<numberOfInputPeers; ip1++) {
						for(int ip2=ip1+1; ip2<numberOfInputPeers; ip2++) {
							// We need 2 multiplications for each pair:
							// 1. [equal]*[value1],   2. [equal]*[value2]
							long value1Share = getPeerInfoByIndex(ip1).getInitialValueShares()[s][element];
							long value2Share = getPeerInfoByIndex(ip2).getInitialValueShares()[s][element];

							long eqShare = equalShares[s][element][ip1][ip2];

							operationIDs[opCount]=opCount;
							primitives.multiply(opCount++, new long[]{eqShare, value1Share});
							operationIDs[opCount]=opCount;
							primitives.multiply(opCount++, new long[]{eqShare, value2Share});
						}
					}
				}
			}
		}
	}

	/**
	 * Retrieves the values [k1==k2]*v1 and [k1==k2]*v2 for each key pair and computes
	 * the aggregated values for all input peers.
	 */
	public void retrieveCollisionResolutionValueAggregation() {
		// stores (k_x==k_y)*value_x at position [x][y]
		long[][][][] leftValueIfEqualKeys = new long[S][H][numberOfInputPeers][numberOfInputPeers];

		// retrieve products
		int opCount = 0;
		for (int s = 0; s < S; s++) {
			for (int element = 0; element < H; element++) {
				if (!lessThans[s][element]) {
					for (int ip1 = 0; ip1 < numberOfInputPeers; ip1++) {
						for (int ip2 = ip1 + 1; ip2 < numberOfInputPeers; ip2++) {
							long eq1 = primitives.getResult(opCount++)[0];
							long eq2 = primitives.getResult(opCount++)[0];
							leftValueIfEqualKeys[s][element][ip1][ip2] = eq1;
							leftValueIfEqualKeys[s][element][ip2][ip1] = eq2;
						}
					}
				}
			}
		}

		// Aggregate the values
		ShamirSharing ss = primitives.getMpcShamirSharing();
		aggrValuesPerKeyShares = new long[S][H][numberOfInputPeers];
		for (int s = 0; s < S; s++) {
			for (int element = 0; element < H; element++) {
				if (!lessThans[s][element]) {
					for (int ip1 = 0; ip1 < numberOfInputPeers; ip1++) {
						// We conditionally add the values of ip2 to the aggregate value of ip1
						for (int ip2 = 0; ip2 < numberOfInputPeers; ip2++) {
							if(ip1 == ip2) {
								// Add the initial value of ip1
								aggrValuesPerKeyShares[s][element][ip1] = ss.modAdd(
										aggrValuesPerKeyShares[s][element][ip1],
										getPeerInfoByIndex(ip1).getInitialValueShares()[s][element]);
							} else {
								// If the two keys are equal, add the value of ip2
								aggrValuesPerKeyShares[s][element][ip1] = ss.modAdd(
										aggrValuesPerKeyShares[s][element][ip1],
										leftValueIfEqualKeys[s][element][ip2][ip1]);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Initializes the last step of the collision resolution: finding the key that
	 * contributes most to each aggregate value.
	 */
	public void initMaximumSearch() {
		// Init the maximum variables
		maxKeyPerSlotShares = new long[S][H];
		maxValuePerSlotShares = new long[S][H];

		// use values of this IP for initialization
		int ipIndex = 0;

		// Init all keys/values with the first key/value in each slot
		for(int s=0; s<S; s++) {
			for(int element=0; element<H; element++) {
				if (!lessThans[s][element]) {
					maxKeyPerSlotShares[s][element] = getPeerInfoByIndex(ipIndex).getInitialKeyShares()[s][element];
					maxValuePerSlotShares[s][element] = aggrValuesPerKeyShares[s][element][ipIndex];
				}
			}
		}
	}

	/**
	 * Schedule one round of maximum comparisons across all slots.
	 * @param ipIndex Compares the current maximum value with the one of input peer ipIndex.
	 */
	public void scheduleMaximumComparisons(int ipIndex) {

		/*
		 * TODO: For k<=17 (S=2), collision resolution takes around 15s. Then, for k=18 and
		 * above, it takes only 4s and scales with k. Why is this gap between 17 and 18??
		 * As a work-around, add dummy operations to sustain a minimum operation load.
		 */
		int operationCount = Math.max(collidingSlotCount, 40);
//		int operationCount = collidingSlotCount;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];
		int opCount=0;

		int firstElementIndex=-1;
		for (int s = 0; s < S; s++) {
			for (int element = 0; element < H; element++) {
				if (!lessThans[s][element]) {
					operationIDs[opCount] = opCount;

					long[] data = new long[5];
					data[0] = maxValuePerSlotShares[s][element];
					data[1] = aggrValuesPerKeyShares[s][element][ipIndex];
					data[2] = -1;
					data[3] = -1;
					data[4] = -1;
					primitives.lessThan(opCount++, data);

					if (firstElementIndex<0) {
						firstElementIndex = element;
					}
				}
			}
		}

		// Add the dummy operations
		while(opCount<operationCount) {
			operationIDs[opCount] = opCount;

			// We're reusing random numbers, so also use the same elements.
			long[] data = new long[5];
			data[0] = maxValuePerSlotShares[0][firstElementIndex];
			data[1] = aggrValuesPerKeyShares[0][firstElementIndex][ipIndex];
			data[2] = -1;
			data[3] = -1;
			data[4] = -1;

			primitives.lessThan(opCount++, data);
		}

	}

	/**
	 * Schedule one round of maximum selection across all slots.
	 * @param ipIndex the IP index.
	 */
	public void scheduleMaximumSelection(int ipIndex) {
		// retrieve the lessThans
		int opCount=0;
		long compShares[][] = new long[S][H];
		for (int s = 0; s < S; s++) {
			for (int element = 0; element < H; element++) {
				if (!lessThans[s][element]) {
					compShares[s][element] = primitives.getResult(opCount++)[0];
				}
			}
		}

		// Schedule the condition selections. If a lessThan is true, we select the new IP's value/key,
		// otherwise the old maximum value/key is selected.
		int operationCount = collidingSlotCount*4;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];
		opCount=0;

		ShamirSharing ss = primitives.getMpcShamirSharing();
		for (int s = 0; s < S; s++) {
			for (int element = 0; element < H; element++) {
				if (!lessThans[s][element]) {
					operationIDs[opCount] = opCount;
					long notCompShare = ss.modSubtract(1, compShares[s][element]);

					primitives.multiply(opCount++, new long[]{compShares[s][element], aggrValuesPerKeyShares[s][element][ipIndex]});
					primitives.multiply(opCount++, new long[]{compShares[s][element], getPeerInfoByIndex(ipIndex).getInitialKeyShares()[s][element]});
					primitives.multiply(opCount++, new long[]{notCompShare, maxValuePerSlotShares[s][element]});
					primitives.multiply(opCount++, new long[]{notCompShare, maxKeyPerSlotShares[s][element]});
				}
			}
		}
	}

	/**
	 * Retrieves one round of maximum selection across all slots. Computes the new maxima.
	 * @param ipIndex the IP index.
	 */
	public void retrieveMaximumSelection(int ipIndex) {
		int opCount=0;
		ShamirSharing ss = primitives.getMpcShamirSharing();
		for (int s = 0; s < S; s++) {
			for (int element = 0; element < H; element++) {
				if (!lessThans[s][element]) {
					long compNewValueShare =  primitives.getResult(opCount++)[0];
					long compNewKeyShare =  primitives.getResult(opCount++)[0];
					long notCompOldMaxValueShare =  primitives.getResult(opCount++)[0];
					long notCompOldMaxKeyShare =  primitives.getResult(opCount++)[0];

					maxValuePerSlotShares[s][element]= ss.modAdd(compNewValueShare, notCompOldMaxValueShare);
					maxKeyPerSlotShares[s][element]= ss.modAdd(compNewKeyShare, notCompOldMaxKeyShare);
				}
			}
		}

	}

	/**
	 * Reconstructs intermediate values of collision resolution. Only for debugging purposes.
	 */
	protected void scheduleDebugReconstruction() {
		// dot it just for one sketch.
		int s=0;
		int operationCount = (collidingSlotCount/S)*3*numberOfInputPeers + (collidingSlotCount/S)*2;
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];

		int opCount=0;
		for(int element=0; element<H; element++) {
			if(!lessThans[s][element]) {
				for(int ip=0; ip<numberOfInputPeers; ip++) {
					operationIDs[opCount]=opCount;
					primitives.reconstruct(opCount++, new long[]{getPeerInfoByIndex(ip).getInitialKeyShares()[s][element]});
					operationIDs[opCount]=opCount;
					primitives.reconstruct(opCount++, new long[]{getPeerInfoByIndex(ip).getInitialValueShares()[s][element]});
					operationIDs[opCount]=opCount;
					primitives.reconstruct(opCount++, new long[]{aggrValuesPerKeyShares[s][element][ip]});
				}
				operationIDs[opCount]=opCount;
				primitives.reconstruct(opCount++, new long[]{maxKeyPerSlotShares[s][element]});
				operationIDs[opCount]=opCount;
				primitives.reconstruct(opCount++, new long[]{maxValuePerSlotShares[s][element]});
			}
		}

	}

	/**
	 * Retrieves reconstructed intermediate values of collision resolution. Only for debugging purposes.
	 */
	protected void retrieveDebugReconstruction() {
		// dot it just for one sketch.
		int s=0;

		StringBuilder sb = new StringBuilder("+++Collision resolution aggregation phase finished:");
		int opCount=0;
		for(int element=0; element<H; element++) {
			if(!lessThans[s][element]) {
				sb.append("\nElement "+element+": ");
				for(int ip=0; ip<numberOfInputPeers; ip++) {
					long key = primitives.getResult(opCount++)[0];
					long value = primitives.getResult(opCount++)[0];
					long aggrValue = primitives.getResult(opCount++)[0];

					sb.append("IP"+ip+": ("+key+", "+value+"/"+aggrValue+")  ");
				}
				long maxkey= primitives.getResult(opCount++)[0];
				long maxvalue= primitives.getResult(opCount++)[0];
				sb.append(" => WINNER is ("+maxkey+", "+maxvalue+")" );
			}
		}

		logger.info(sb.toString());
	}


	/**
	 * Reconstructs those values above Tau with their corresponding keys.
	 */
	public void scheduleFinalResultReconstrution() {

		// Start reconstruction of winning keys and aggregate values for each slot
		int operationCount = 2*collidingSlotCount;
		if(S*K*2 < 2*collidingSlotCount) { // (reconstruct at most K key/value tuples per sketch)
			operationCount = S*K*2;
		}
		initializeNewOperationSet(operationCount);
		operationIDs = new int[operationCount];
		int opCount=0;
		for(int s=0; s<S; s++) {
			// Only reconstruct K items per hash array (in case multiple have the same values)
			int elCount = 0;
			for(int element=0; element<H; element++) {
				if(!lessThans[s][element] && elCount<K) {
					// This element has to be reconstructed.
					operationIDs[opCount]=opCount;
					primitives.reconstruct(opCount++, new long[]{maxKeyPerSlotShares[s][element]});
					operationIDs[opCount]=opCount;
					primitives.reconstruct(opCount++, new long[]{maxValuePerSlotShares[s][element]});
					elCount++;
				}
			}
		}
	}

}
