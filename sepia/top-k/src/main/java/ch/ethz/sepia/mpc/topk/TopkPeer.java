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

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.events.FinalResultEvent;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.VectorData;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import ch.ethz.sepia.services.DirectoryPoller;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;
import ch.ethz.sepia.startup.Configuration;

/**
 * A MPC peer providing the private input data for the topk protocol
 *
 * @author Dilip Many
 *
 */
public class TopkPeer extends TopkBase {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(TopkPeer.class));

	/** vector of protocols (between this peer and the privacy peers) */
	private Vector<TopkProtocolPeer> peerProtocolThreads = null;
	/** MpcShamirSharing instance to use basic operations on Shamir shares */
	protected ShamirSharing mpcShamirSharing = null;

	/** barrier to synchronize the protocol threads of this peer*/
	private CyclicBarrier protocolThreadsBarrier = null;


	/** input data of this peer  */
	protected TopKData topkData = null;
	protected DirectoryPoller poller;
	/** array containing my initial shares; dimensions: [S][numberOfPrivacyPeers][H] */
	private long[][][] initialValueShares = null;
	private long[][][] initialKeyShares = null;

	/** hashed keys. Dimension: [S][H] */
	protected long[][] keys;
	/** hashed values. Dimension: [S][H] */
	protected long[][] values;

	public static final String PROP_TOPK_SEED = "mpc.topk.seed"; // initial seed
	public static final String PROP_TOPK_INPUT_TYPE = "mpc.topk.inputType"; // the input data type
	public static final String DEFAULT_TOPK_INPUT_TYPE = "BasicMetricsBinary"; // the default value of input data type
	/** Random seed for hash functions */
	protected int seed;
	/** The input data type **/
	protected String inputType;


	private boolean readOperationSuccessful;

	/**
	 * constructs a new topk peer object
	 *
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm the connection manager
	 * @throws Exception
	 */
	public TopkPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);
		peerProtocolThreads = new Vector<TopkProtocolPeer>();
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

		openInputDirectory(inputFolder);

	}

	/**
	 * Initializes and starts a new round of computation. It first (re-)established connections and
	 * then creates and runs the protocol threads for the new round.
	 */
	protected void initializeNewRound() {
		PrimitivesEnabledProtocol.newStatisticsRound();

		List<String> privacyPeerIDs = connectionManager.getActivePeers(true);
		Collections.sort(privacyPeerIDs);
		numberOfPrivacyPeers = privacyPeerIDs.size();
		mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
		mpcShamirSharing.init();
		clearPP2PPBarrier();

		protocolThreadsBarrier = new CyclicBarrier(numberOfPrivacyPeers);

		// Init state variables
		finalResults = null;
		finalResultsToDo = numberOfPrivacyPeers;
		initialValueShares = null;
		initialKeyShares = null;
		keys = null;
		values = null;

		readNextRecord();
		createProtocolThreadsForPrivacyPeers(privacyPeerIDs);
	}


	/**
	 * Create and start the threads. Attach one privacy peer id to each of them.
	 *
	 * @param privacyPeerIDs the ids of the privacy peers
	 */
	private void createProtocolThreadsForPrivacyPeers(List<String> privacyPeerIDs)  {
		peerProtocolThreads.clear();
		int currentID = 0;
		for(String ppId: privacyPeerIDs) {
			logger.info("Create a thread for privacy peer " +ppId );
			TopkProtocolPeer topkProtocolPeer = new TopkProtocolPeer(currentID, this, ppId, currentID, stopper);
			topkProtocolPeer.addObserver(this);
			Thread thread = new Thread(topkProtocolPeer, "Topk Peer protocol with user number " + currentID);
			peerProtocolThreads.add(topkProtocolPeer);
			thread.start();
			currentID++;
		}
	}

	/**
	 * Generates the S hash arrays of size H for values and keys.
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	private void hashData() throws NoSuchAlgorithmException, IOException {
		keys = new long[S][H];
		values = new long[S][H];

		Hash h = new Hash(H);
		for(int sketch=0; sketch<S; sketch++) {
			/*
			 * We need pairwise independent hash functions. Therefore we set the seed for the i-th hash
			 * function to i*seed. If the hash function is good, this should be sufficient.
			 */
			h.setSeed((sketch + 1) * seed);

			HashMap<Integer, Integer> map = topkData.getDistribution();
			for (Entry<Integer, Integer> mapping : map.entrySet()) {
				int hashvalue = h.getHash(mapping.getKey());

				/*
				 * Replace an entry if the value of the current entry is smaller than the one to be inserted.
				 */
				if (values[sketch][hashvalue] < mapping.getValue()) {
					keys[sketch][hashvalue] = mapping.getKey();
					values[sketch][hashvalue] = mapping.getValue();
				}
			}
		}
	}

	/**
	 * Generates shares for each secret input.
	 */
	public void generateInitialShares() {
		logger.info("Generating initial shares...");
		initialValueShares = new long[S][numberOfPrivacyPeers][H];
		initialKeyShares = new long[S][numberOfPrivacyPeers][H];
		for(int sketch=0; sketch<S; sketch++) {
			initialValueShares[sketch] = mpcShamirSharing.generateShares(values[sketch]);
			initialKeyShares[sketch] = mpcShamirSharing.generateShares(keys[sketch]);
		}
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
		if (object instanceof TopkMessage) {
			// We are awaiting a final results message
			TopkMessage message = (TopkMessage) object;

			if(message.isDummyMessage()) {
				// Simulate a final results message in order not to stop protocol execution
				message.setIsFinalResultMessage(true);
			}

			if(message.isFinalResultMessage()) {
				logger.info("Received a final result message from a privacy peer");
				finalResultsToDo--;

				if (finalResults == null && message.getFinalResults() != null) {
					finalResults = message.getFinalResults();
				}

				if(finalResultsToDo <= 0) {
					// notify observers about final result
					logger.info("Received all final results. Notifying observers...");
					VectorData dummy = new VectorData(); // dummy data to avoid null pointer exception in Peers::processMpcEvent
					FinalResultEvent finalResultEvent = new FinalResultEvent(this, myAlphaIndex, getMyPeerID(), message.getSenderID(), dummy);
					finalResultEvent.setVerificationSuccessful(true);
					sendNotification(finalResultEvent);

					writeOutputToFile();

					// check if there are more time slots to process
					if(currentTimeSlot < timeSlotCount) {
						currentTimeSlot++;
						initializeNewRound();
					} else {
						logger.info("No more data available... Stopping protocol threads...");
						protocolStopper.stop();
					}
				}
			} else {
				String errorMessage = "Didn't receive final result...";
				errorMessage += "\nisGoodBye: "+message.isGoodbyeMessage();
				errorMessage += "\nisHello: "+message.isHelloMessage();
				errorMessage += "\nisInitialShares: "+message.isInitialSharesMessage();
				errorMessage += "\nisFinalResult: "+message.isFinalResultMessage();
				logger.error(errorMessage);
				sendExceptionEvent(this, errorMessage);
			}
		} else {
			throw new Exception("Received unexpected message type (expected: " + TopkMessage.class.getName() + ", received: " + object.getClass().getName());
		}
	}


	public void openInputDirectory(String inputFolderName) throws Exception {
		poller = new DirectoryPoller(stopper, new File(inputFolderName));

		if(inputType.equals("TopkKeyWeightData")) {
			topkData = new TopkKeyWeightData();
		} else {
			// BasicMetricsBinary by default
			topkData = new SiteData();
		}
	}


	/**
	 * Reads the next record from the input file. It also pre-processes the data, i.e., it hashes
	 * keys and values in S hash arrays.
	 *
	 * @return true if successful. False if an error occurred or no more data available.
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	public boolean readNextRecord()  {
		readOperationSuccessful = false;
		try {
			long start = System.currentTimeMillis();
			topkData.setInputFile(poller.getNextFile());
			topkData.openFile();
			topkData.readNextTimeslot();
			hashData();
			readOperationSuccessful = true;
			logger.info("INPUT: Time for reading and preprocessing distribution: "+(System.currentTimeMillis()-start)+"ms");
		} catch (NoSuchAlgorithmException e) {
			String errorMessage = "Error when hashing data: " + Utils.getStackTrace(e);
	        logger.error(errorMessage);
		} catch (IOException e) {
			String errorMessage = "Error when reading next record: " + Utils.getStackTrace(e);
	        logger.error(errorMessage);
		} catch (Exception e) {
			String errorMessage = "Error when reading next record: " + Utils.getStackTrace(e);
	        logger.error(errorMessage);
		}
		return readOperationSuccessful;
	}

	 /**
     * Write the output to a file.
     * @throws Exception
     */
	protected void writeOutputToFile() throws Exception {
		String fileName = outputFolder + "/" + "topk_output_"
				+ "_round" + String.format("%03d", currentTimeSlot)+ ".txt";

		StringBuilder line = new StringBuilder("Key; Value\n");

		for(Entry<Long, Long> entry:finalResults.entrySet()) {
			if(areKeysIpAddresses()) {
				line.append(renderIPV4Address(entry.getKey()) +"; "+entry.getValue()+"\n");
			} else {
				line.append(entry.getKey() +"; "+entry.getValue()+"\n");
			}
		}

		Services.writeFile(line.toString(), fileName);
	}

	/**
	 * Infers from input file name whether it is a distribution of IP addresses or not.
	 * @return true if keys are IP addresses.
	 */
	public boolean areKeysIpAddresses() {
		return topkData.isIPv4Distribution();

	}

	@Override
	protected synchronized void initProperties() throws Exception {
		super.initProperties();

        // TODO: What is the right "peerName" here?
        Properties properties = Configuration.getInstance("peerName").getProperties();

		// Set properties specific to top-k input peers
        seed = Integer.valueOf(properties.getProperty(PROP_TOPK_SEED));
        inputType = properties.getProperty(PROP_TOPK_INPUT_TYPE, DEFAULT_TOPK_INPUT_TYPE);
        logger.info("Top-k parameter seed="+seed);
	}

	public CyclicBarrier getProtocolThreadsBarrier() {
		return protocolThreadsBarrier;
	}

	/**
	 * Gets the key shares for one privacy peer.
	 * @param ppNr the PP number
	 * @return the shares. Dimensions: [S][H].
	 */
	public long[][] getInitialKeySharesForPP(int ppNr) {
		long[][] shares = new long[S][H];
		for(int sketch=0; sketch<S; sketch++) {
			shares[sketch] = initialKeyShares[sketch][ppNr];
		}

		return shares;
	}

	/**
	 * Gets the value shares for one privacy peer.
	 * @param ppNr the PP number
	 * @return the shares. Dimensions: [S][H].
	 */
	public long[][] getInitialValueSharesForPP(int ppNr) {
		long[][] shares = new long[S][H];
		for(int sketch=0; sketch<S; sketch++) {
			shares[sketch] = initialValueShares[sketch][ppNr];
		}
		return shares;
	}

}
