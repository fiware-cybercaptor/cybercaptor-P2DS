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

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.PrivacyViolationException;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesException;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * Protocol between a privacy peer and another privacy peer for the ADDITIVE protocol.
 *
 * @author Dilip Many
 *
 */
public class AdditiveProtocolPrivacyPeerToPP extends AdditiveProtocol {
    private static final XLogger logger = new XLogger(LoggerFactory.getLogger(AdditiveProtocolPrivacyPeerToPP.class));

	/** reference to ADDITIVE privacy peer object that started this protocol instance */
	protected AdditivePrivacyPeer privacyPeer;



	/**
	 * Creates a new instance of the ADDITIVE protocol between two privacy peers.
	 *
	 * @param threadNumber				Protocol's thread number
	 * @param privacyPeer		the privacy peer instantiating this thread
	 * @param privacyPeerID				the counterpart privacy peer
	 * @param privacyPeerIndex			the counterpart privacy peer's index
	 * @param stopper					Stopper to stop protocol thread
	 * @throws Exception
	 */

	public AdditiveProtocolPrivacyPeerToPP(int threadNumber, AdditivePrivacyPeer privacyPeer, String privacyPeerID, int privacyPeerIndex, Stopper stopper) {
		super(threadNumber, privacyPeer, privacyPeerID, privacyPeerIndex, stopper);
		this.privacyPeer = privacyPeer;
	}


	/**
	 * Run the MPC ADDITIVE protocol for the privacy peer
	 */
	public synchronized void run() {
		initialize(privacyPeer.getTimeSlotCount(), privacyPeer.getNumberOfItems(), privacyPeer.getNumberOfInputPeers());

		// wait for all shares
		logger.info("thread " + Thread.currentThread().getId() + " waits for all shares to arrive");
		privacyPeer.waitForNextPPProtocolStep();
		if(wasIStopped()) {
			return;
		}

		CyclicBarrier ppThreadsBarrier = privacyPeer.getBarrierPP2PPProtocolThreads();
		try {
			/*
			 * One thread always prepares the data for the next step and then all threads
			 * enter doOperations() and process the operations in parallel.
			 */

            // Check if we need to do input verification
            if(!privacyPeer.skipInputVerification()) {
                if (ppThreadsBarrier.await()==0) {
                	privacyPeer.startLessThans();
                }
                ppThreadsBarrier.await();
                if(!doOperations()){
                	logger.error("Less-thans failed; returning");
    				return;
    			}

                if (ppThreadsBarrier.await()==0) {
                	privacyPeer.startNormBoundCheckResultReconstruction();
                }
                ppThreadsBarrier.await();
            	if(!doOperations()) {
            		logger.error("Norm bound result reconstruction failed; returning");
					return;
				}

            	if (ppThreadsBarrier.await()==0) {
            		privacyPeer.processNormBoundCheckResult();
            	}
            	ppThreadsBarrier.await();
            }

            if(privacyPeer.isRoundSuccessful()) {
	            if (ppThreadsBarrier.await()==0) {
					privacyPeer.addShares();
		            privacyPeer.startFinalResultReconstruction();
				}
				ppThreadsBarrier.await();
	            if(!doOperations()){
	            	logger.error(" final result reconstruction failed; returning");
					return;
				}
            }

			if (ppThreadsBarrier.await()==0) {
				privacyPeer.setFinalResult();
				logger.info("ADDITIVE protocol round completed");
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
