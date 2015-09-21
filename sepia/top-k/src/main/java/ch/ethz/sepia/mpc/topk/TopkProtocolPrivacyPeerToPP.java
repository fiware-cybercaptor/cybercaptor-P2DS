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

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.PrivacyViolationException;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesException;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * Protocol between a privacy peer and another privacy peer for the topk
 * protocol.
 *
 * @author Dilip Many
 *
 */
public class TopkProtocolPrivacyPeerToPP extends TopkProtocol {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(TopkProtocolPrivacyPeerToPP.class));

	/**
	 * reference to topk privacy peer object that started this protocol
	 * instance
	 */
	protected TopkPrivacyPeer privacyPeer;

	/**
	 * Creates a new instance of the topk protocol between two privacy
	 * peers.
	 *
	 * @param threadNumber
	 *            Protocol's thread number
	 * @param privacyPeer
	 *            the privacy peer instantiating this thread
	 * @param privacyPeerID
	 *            the counterpart privacy peer
	 * @param privacyPeerIndex
	 *            the counterpart privacy peer's index
	 * @param stopper
	 *            Stopper to stop protocol thread
	 * @throws Exception
	 */

	public TopkProtocolPrivacyPeerToPP(int threadNumber, TopkPrivacyPeer privacyPeer, String privacyPeerID,
			int privacyPeerIndex, Stopper stopper) {
		super(threadNumber, privacyPeer, privacyPeerID, privacyPeerIndex, stopper);
		this.privacyPeer = privacyPeer;
	}

	/**
	 * Run the MPC topk protocol for the privacy peer
	 */
	public synchronized void run() {
		initialize(privacyPeer.getTimeSlotCount(), privacyPeer.getNumberOfItems(), privacyPeer.getNumberOfInputPeers());

		// wait for all shares
		logger.info("thread " + Thread.currentThread().getId() + " waits for all shares to arrive...");
		privacyPeer.waitForNextPPProtocolStep();
		if (wasIStopped()) {
			return;
		}

		CyclicBarrier ppThreadsBarrier = privacyPeer.getBarrierPP2PPProtocolThreads();
		try {
			/*
			 * One thread always prepares the data for the next step and then
			 * all threads enter doOperations() and process the operations in
			 * parallel.
			 */
			// ---------------------------
			// 1. Aggregation of values
			// ---------------------------
			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.aggregateValues();
				privacyPeer.initBinarySearch();
			}
			ppThreadsBarrier.await();

			// ---------------------------
			// 2. Binary search for tau, the value separating the k-th from the
			// (k+1)-th value.
			// ---------------------------
			int bsRound = 0;
			long bsStart = System.currentTimeMillis();
			do {
				bsRound++;

				if (ppThreadsBarrier.await() == 0) {
					logger.info(Services.getFilterPassingLogPrefix() + "Go for binary search round "
							+ bsRound);
					privacyPeer.scheduleBinarySearchComparisons();
					logger.info("Performing Binary Search comparisons...");
				}
				ppThreadsBarrier.await();
				if (!doOperations()) {
					logger.error("Binary search comparisons failed. returning!");
					return;
				}

				if (ppThreadsBarrier.await() == 0) {
					privacyPeer.scheduleBinarySearchBiggercount();
					logger.info("Performing Binary Search biggercount...");
				}
				ppThreadsBarrier.await();
				if (!doOperations()) {
					logger.error("Binary search biggercount failed. returning!");
				}

				if (ppThreadsBarrier.await() == 0) {
					privacyPeer.scheduleBinarySearchBiggercountReconstruction();
					logger.info("Reconstructing biggercount comparisons...");
				}
				ppThreadsBarrier.await();
				if (!doOperations()) {
					logger.error("Biggercount comparisons reconstruction failed. returning!");
					return;
				}

				if (ppThreadsBarrier.await() == 0) {
					privacyPeer.binarySearchDecision();
				}
				ppThreadsBarrier.await();
			} while (!privacyPeer.isBinarySearchFinished());

			// ---------------------------
			// 3. Reconstruct the lessThans that indicate the top-k elements
			// ---------------------------
			if (ppThreadsBarrier.await() == 0) {
				long duration = System.currentTimeMillis() - bsStart;
				logger.info(
						Services.getFilterPassingLogPrefix() + "\n+++----------\nBinary search finished in " + bsRound
								+ " rounds and " + Math.round(duration / 1000.0) + " seconds!\n-------------");
				privacyPeer.scheduleLessThanReconstruction();
			}
			ppThreadsBarrier.await();
			if (!doOperations()) {
				logger.error("LessThan reconstruction failed. returning!");
				return;
			}

			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.retrieveLessThans();
			}
			ppThreadsBarrier.await();

			// ---------------------------
			// 4a. Global collision resolution, aggregate values by key
			// ---------------------------
			long startCollisions = System.currentTimeMillis();
			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.scheduleCollisionResolutionEquals();
				logger.info(Services.getFilterPassingLogPrefix()
						+ "Performing equals for collision resolution ...");
			}
			ppThreadsBarrier.await();
			if (!doOperations()) {
				logger.error("Collision resolution equals failed. returning!");
				return;
			}
			ppThreadsBarrier.await();

			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.retrieveCollisionResolutionEquals();
			}
			ppThreadsBarrier.await();

			/*
			 * NOTE: There is bug in lines 5-6 of Alg. 2 in the ICCCN 2010
			 * paper. -> Check the TISSEC paper for the corrected algorithm.
			 */
			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.scheduleCollisionResolutionValueAggregation();
				logger.info(Services.getFilterPassingLogPrefix()
						+ "Performing value aggregation for collision resolution");
			}
			ppThreadsBarrier.await();
			if (!doOperations()) {
				logger.error("Collision resolution value aggregation failed. returning!");
				return;
			}
			ppThreadsBarrier.await();
			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.retrieveCollisionResolutionValueAggregation();
			}
			ppThreadsBarrier.await();

			// ---------------------------
			// 4b. Global collision resolution, find max key per slot
			// ---------------------------

			ppThreadsBarrier.await();
			if (!doOperations()) {
				logger.error("Random number generation for maximum search failed. returning!");
				return;
			}
			ppThreadsBarrier.await();

			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.initMaximumSearch();
			}
			ppThreadsBarrier.await();
			for (int ip = 1; ip < privacyPeer.getNumberOfInputPeers(); ip++) {
				if (ppThreadsBarrier.await() == 0) {
					privacyPeer.scheduleMaximumComparisons(ip);
					logger.info(Services.getFilterPassingLogPrefix()
							+ "Collision resolution: selecting the maximum value, IP=" + ip);
				}
				ppThreadsBarrier.await();
				if (!doOperations()) {
					logger.error("Maximum value comparison failed. returning!");
					return;
				}

				if (ppThreadsBarrier.await() == 0) {
					privacyPeer.scheduleMaximumSelection(ip);
				}
				ppThreadsBarrier.await();
				if (!doOperations()) {
					logger.error("Maximum value selection failed. returning!");
					return;
				}

				if (ppThreadsBarrier.await() == 0) {
					privacyPeer.retrieveMaximumSelection(ip);
				}
				ppThreadsBarrier.await();
			}

			// DEBUG code:
			// if(ppThreadsBarrier.await()==0) {
			// privacyPeer.scheduleDebugReconstruction();
			// }
			// ppThreadsBarrier.await();
			// if(!doOperations("Collision resolution value aggregation failed. returning!"))
			// {
			// return;
			// }
			// ppThreadsBarrier.await();
			// if(ppThreadsBarrier.await()==0) {
			// privacyPeer.retrieveDebugReconstruction();
			// }
			//
			if (ppThreadsBarrier.await() == 0) {
				long duration = System.currentTimeMillis() - startCollisions;
				logger.info(
						Services.getFilterPassingLogPrefix() + "Collision resolution took "
								+ Math.round(duration / 1000.0) + " seconds!");
			}

			// ---------------------------
			// 5. Reconstruct the Final Results
			// ---------------------------

			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.scheduleFinalResultReconstrution();
				logger.info(Services.getFilterPassingLogPrefix() + "Reconstructing final result...");
			}
			ppThreadsBarrier.await();
			if (!doOperations()) {
				logger.error("Final Result reconstruction failed. returning!");
				return;
			}

			if (ppThreadsBarrier.await() == 0) {
				privacyPeer.setFinalResult();
			}
		} catch (PrimitivesException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (InterruptedException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (BrokenBarrierException e) {
			logger.error(Utils.getStackTrace(e));
		} catch (PrivacyViolationException e) {
			logger.error(Utils.getStackTrace(e));
		}
	}
}
