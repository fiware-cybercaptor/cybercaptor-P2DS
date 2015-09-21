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

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.mpc.additive.AdditivePeerInfo;
import ch.ethz.sepia.mpc.additive.AdditivePrivacyPeer;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.startup.Configuration;

/**
 * Privacy peer for the entropy protocol.
 *
 * @author martibur
 *
 */
public class EntropyPrivacyPeer extends AdditivePrivacyPeer {
    public static final String DEFAULT_TSALLIS_EXPONENT = "2";

    private static final XLogger logger = new XLogger(LoggerFactory
            .getLogger(EntropyPrivacyPeer.class));
    /** The exponent for the tsallis entropy */
    public static final String PROP_TSALLIS_EXPPONENT = "mpc.entropy.tsallisexponent";
    protected long sumOfExponentiatedItemSumsShare;

    protected long totalSumShare;
    protected long tsallisExponent;

    public EntropyPrivacyPeer(final String peerName, final int myPeerIndex,
            final ConnectionManager cm, final Stopper stopper) throws Exception {
        super(peerName, myPeerIndex, cm, stopper);
    }

    /**
     * computes the share of the sum of the exponentiated item sums
     */
    public synchronized void computeSumOfExponentiatedItemSums() {
        for (int operationID : this.operationIDs) {
            this.sumOfExponentiatedItemSumsShare = this.primitives
                    .getMpcShamirSharing().modAdd(
                            this.sumOfExponentiatedItemSumsShare,
                            this.primitives.getResult(operationID)[0]);
        }
        logger.info("Computed share of the sum of the exponentiated item sums");
    }

    /**
     * computes the total sum (N)
     */
    public void computeTotalSum() {
        for (int itemIndex = 0; itemIndex < this.numberOfItems; itemIndex++) {
            this.totalSumShare = this.primitives.getMpcShamirSharing().modAdd(
                    this.totalSumShare, getItemSumShares()[itemIndex]);
        }
        logger.info("Computed the share of the total sum (N)");
    }

    /**
     * Create and start the threads. Attach one privacy peer id to each of them.
     *
     * @param privacyPeerIDs
     *            the ids of the privacy peers
     * @param ppIndexMap
     *            a map mapping privacy peer IDs to indices
     */
    protected void createProtocolThreadsForPrivacyPeers(
            final List<String> privacyPeerIDs,
            final Map<String, Integer> ppIndexMap) {
        getPpToPPProtocolThreads().clear();
        getPrivacyPeerInfos().clear();
        int currentID = 0;
        for (String ppId : privacyPeerIDs) {
            logger.info("Create a thread for privacy peer " + ppId);
            int otherPPindex = ppIndexMap.get(ppId);
            EntropyProtocolPrivacyPeerToPP pp2pp = new EntropyProtocolPrivacyPeerToPP(
                    currentID, this, ppId, otherPPindex, this.stopper);
            pp2pp.setMyPeerIndex(this.myAlphaIndex);
            pp2pp.addObserver(this);
            Thread thread = new Thread(pp2pp,
                    "Entropy PP-to-PP protocol connected with " + ppId);
            getPpToPPProtocolThreads().add(pp2pp);
            getPrivacyPeerInfos().add(currentID,
                    new AdditivePeerInfo(ppId, otherPPindex));
            thread.start();
            currentID++;
        }
    }

    @Override
    public void initializeNewRound() {
        this.totalSumShare = 0;
        this.sumOfExponentiatedItemSumsShare = 0;
        super.initializeNewRound();
    }

    @Override
    protected synchronized void initProperties() throws Exception {
        super.initProperties();
        Properties properties = Configuration.getInstance(this.myPeerName)
                .getProperties();
        this.tsallisExponent = Integer.valueOf(properties.getProperty(
                PROP_TSALLIS_EXPPONENT, DEFAULT_TSALLIS_EXPONENT));
        if (this.tsallisExponent <= 1) {
            logger.error("Tsallis exponent must be > 1 (found: "
                    + this.tsallisExponent + ")! Setting it to 2.");
            this.tsallisExponent = 2;
        }
    }

    /**
     * retrieves and stores the final result
     */
    @Override
    public void setFinalResult() {
        logger.info("Thread " + Thread.currentThread().getId()
                + " called setFinalResult");

        double entropyValue = 0;
        if (this.primitives.getResult(0)[0] > 0) {
            entropyValue = 1 / Math.pow(this.primitives.getResult(0)[0],
                    this.tsallisExponent);
            entropyValue = 1 - entropyValue * this.primitives.getResult(1)[0];
            entropyValue *= 1 / ((double) this.tsallisExponent - 1);
        }
        logger.info("totalSum=" + this.primitives.getResult(0)[0]
                + ", sumOfExponentiatedItemSums="
                + this.primitives.getResult(1)[0] + ", tsallisExponent="
                + this.tsallisExponent + ", entropyValue=" + entropyValue);

        // send back the entropy value in per mille
        this.finalResults = new long[1];
        this.finalResults[0] = Math.round(1000 * entropyValue);

        logger.info("Start next pp-peer protocol step");
        startNextPeerProtocolStep();
    }

    /**
     * starts the exponentiation of the item sums
     */
    public synchronized void startExponentiation() {
        initializeNewOperationSet(this.numberOfItems);
        this.operationIDs = new int[this.numberOfItems];
        long[] data = null;
        for (int itemIndex = 0; itemIndex < this.numberOfItems; itemIndex++) {
            this.operationIDs[itemIndex] = itemIndex;
            data = new long[2];
            data[0] = getItemSumShares()[itemIndex];
            data[1] = this.tsallisExponent;
            if (!this.primitives.power(itemIndex, data)) {
                logger.error("power operation arguments are invalid: id="
                        + itemIndex + ", data=" + data[0] + "," + data[1]);
            }
        }
        logger.info("Started the exponentiation of the item sums; ("
                + this.operationIDs.length
                + " power operations are in progress)");
    }

    /**
     * starts the reconstruction of the total sum (N) and the sum of the
     * exponentiated item sums
     */
    public synchronized void startSumsReconstruction() {
        initializeNewOperationSet(2);
        this.operationIDs = new int[2];
        this.operationIDs[0] = 0;
        this.operationIDs[1] = 1;

        long[] data = new long[1];
        data[0] = this.totalSumShare;
        if (!this.primitives.reconstruct(0, data)) {
            logger.error("reconstruct operation arguments are invalid: id=0, data="
                    + data[0]);
        }
        data = new long[1];
        data[0] = this.sumOfExponentiatedItemSumsShare;
        if (!this.primitives.reconstruct(1, data)) {
            logger.error("reconstruct operation arguments are invalid: id=1, data="
                    + data[0]);
        }
        logger.info("Started the reconstruction of the total sum (N) and the sum of the exponentiated item sums.");
    }

}
