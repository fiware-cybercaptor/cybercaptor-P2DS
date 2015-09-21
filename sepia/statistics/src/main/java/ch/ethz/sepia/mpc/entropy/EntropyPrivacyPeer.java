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

package ch.ethz.sepia.mpc.entropy;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.mpc.additive.AdditivePeerInfo;
import ch.ethz.sepia.mpc.additive.AdditivePrivacyPeer;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.ConfigFile;

/**
 * Privacy peer for the entropy protocol.
 * @author martibur
 *
 */
public class EntropyPrivacyPeer extends AdditivePrivacyPeer {
    private static final Logger logger = LogManager.getLogger(EntropyPrivacyPeer.class);
    
	protected long totalSumShare;
	protected long tsallisExponent;
	protected long sumOfExponentiatedItemSumsShare;

	/** The exponent for the tsallis entropy */
	public static final String PROP_TSALLIS_EXPPONENT = "mpc.entropy.tsallisexponent";
	public static final String DEFAULT_TSALLIS_EXPONENT = "2";

	
	public EntropyPrivacyPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);
	}
	
	@Override
	protected synchronized void initProperties() throws Exception {
		super.initProperties();
		Properties properties = ConfigFile.getInstance().getProperties();
		tsallisExponent = Integer.valueOf(properties.getProperty(PROP_TSALLIS_EXPPONENT, DEFAULT_TSALLIS_EXPONENT));
		if (tsallisExponent <= 1) {
			logger.error("Tsallis exponent must be > 1 (found: " + tsallisExponent + ")! Setting it to 2.");
			tsallisExponent = 2;
		}
	}
	
	@Override
	public void initializeNewRound() {
		totalSumShare = 0;
		sumOfExponentiatedItemSumsShare = 0;
		super.initializeNewRound();
	}
	
	/**
	 * retrieves and stores the final result
	 */
	public void setFinalResult() {
		logger.info("Thread " + Thread.currentThread().getId() + " called setFinalResult");

		double entropyValue = 0;
		if (primitives.getResult(0)[0] > 0) {
			entropyValue = 1 / Math.pow(primitives.getResult(0)[0], tsallisExponent);
			entropyValue = 1 - (entropyValue * (double) primitives.getResult(1)[0]);
			entropyValue *= 1 / ((double) tsallisExponent - 1);
		}
		logger.info("totalSum=" + primitives.getResult(0)[0] + ", sumOfExponentiatedItemSums=" + primitives.getResult(1)[0]
						+ ", tsallisExponent=" + tsallisExponent+ ", entropyValue="+entropyValue);

		// send back the entropy value in per mille
		finalResults = new long[1];
		finalResults[0] = Math.round(1000 * entropyValue);

		logger.info("Start next pp-peer protocol step");
		startNextPeerProtocolStep();
	}

	/**
	 * Create and start the threads. Attach one privacy peer id to each of them.
	 * 
	 * @param privacyPeerIDs
	 *            the ids of the privacy peers
	 * @param ppIndexMap
	 * 			  a map mapping privacy peer IDs to indices
	 */
	protected void createProtocolThreadsForPrivacyPeers(List<String> privacyPeerIDs, Map<String, Integer> ppIndexMap) {
		getPpToPPProtocolThreads().clear();
		getPrivacyPeerInfos().clear();
		int currentID =0;
		for(String ppId: privacyPeerIDs) {
			logger.info("Create a thread for privacy peer " +ppId );
			int otherPPindex = ppIndexMap.get(ppId);
			EntropyProtocolPrivacyPeerToPP pp2pp = new EntropyProtocolPrivacyPeerToPP(currentID, this, ppId, otherPPindex, stopper);
			pp2pp.setMyPeerIndex(myAlphaIndex);
			pp2pp.addObserver(this);
			Thread thread = new Thread(pp2pp, "Entropy PP-to-PP protocol connected with " + ppId);
			getPpToPPProtocolThreads().add(pp2pp);
			getPrivacyPeerInfos().add(currentID, new AdditivePeerInfo(ppId, otherPPindex));
			thread.start();
			currentID++;
		}
	}

	/**
	 * computes the total sum (N)
	 */
	public void computeTotalSum() {
		for(int itemIndex = 0; itemIndex < numberOfItems; itemIndex++) {
			totalSumShare = primitives.getMpcShamirSharing().modAdd(totalSumShare, getItemSumShares()[itemIndex]);
		}
		logger.info("Computed the share of the total sum (N)");
	}


	/**
	 * starts the exponentiation of the item sums
	 */
	public synchronized void startExponentiation() {
		initializeNewOperationSet(numberOfItems);
		operationIDs = new int[numberOfItems];
		long[] data = null;
		for(int itemIndex = 0; itemIndex < numberOfItems; itemIndex++) {
			operationIDs[itemIndex] = itemIndex;
			data = new long[2];
			data[0] = getItemSumShares()[itemIndex];
			data[1] = tsallisExponent;
			if(!primitives.power(itemIndex, data)) {
				logger.error("power operation arguments are invalid: id="+itemIndex+", data="+data[0]+","+data[1]);
			}
		}
		logger.info("Started the exponentiation of the item sums; (" + operationIDs.length + " power operations are in progress)");
	}


	/**
	 * computes the share of the sum of the exponentiated item sums
	 */
	public synchronized void computeSumOfExponentiatedItemSums() {
		for(int i = 0; i < operationIDs.length; i++) {
			sumOfExponentiatedItemSumsShare = primitives.getMpcShamirSharing().modAdd(sumOfExponentiatedItemSumsShare, primitives.getResult(operationIDs[i])[0]);
		}
		logger.info("Computed share of the sum of the exponentiated item sums");
	}


	/**
	 * starts the reconstruction of the total sum (N) and the sum of the exponentiated item sums
	 */
	public synchronized void startSumsReconstruction() {
		initializeNewOperationSet(2);
		operationIDs = new int[2];
		operationIDs[0] = 0;
		operationIDs[1] = 1;

		long[] data = new long[1];
		data[0] = totalSumShare;
		if(!primitives.reconstruct(0, data)) {
			logger.error("reconstruct operation arguments are invalid: id=0, data="+data[0]);
		}
		data = new long[1];
		data[0] = sumOfExponentiatedItemSumsShare;
		if(!primitives.reconstruct(1, data)) {
			logger.error("reconstruct operation arguments are invalid: id=1, data="+data[0]);
		}
		logger.info("Started the reconstruction of the total sum (N) and the sum of the exponentiated item sums.");
	}

}
