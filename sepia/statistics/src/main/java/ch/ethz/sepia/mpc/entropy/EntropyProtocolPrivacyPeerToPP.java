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

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.connections.PrivacyViolationException;
import ch.ethz.sepia.mpc.additive.AdditiveProtocolPrivacyPeerToPP;
import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesException;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * Protocol between entropy privacy peers.
 * @author martibur
 *
 */
public class EntropyProtocolPrivacyPeerToPP extends AdditiveProtocolPrivacyPeerToPP {
    private static final Logger logger = LogManager.getLogger(EntropyProtocolPrivacyPeerToPP.class);
    
	public EntropyProtocolPrivacyPeerToPP(int threadNumber, EntropyPrivacyPeer privacyPeer, String privacyPeerID,
			int privacyPeerIndex, Stopper stopper) {
		super(threadNumber, privacyPeer, privacyPeerID, privacyPeerIndex, stopper);
	}

	/**
	 * Run the MPC entropy protocol for the privacy peer
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
	            
	            // Now do the entropy computation
	            EntropyPrivacyPeer epp = (EntropyPrivacyPeer)privacyPeer;
	            
	            if (ppThreadsBarrier.await()==0) {
	            	logger.info("thread " + Thread.currentThread().getId() + " starts entropy computation part");
	            	epp.computeTotalSum();
	    			epp.startExponentiation();
	            }
				ppThreadsBarrier.await();
    			if(!doOperations()){
    				logger.error("Exponentiation of the item sums failed; returning");
    				return;
    			}
    			
    			
    			if (ppThreadsBarrier.await()==0) {
    				epp.computeSumOfExponentiatedItemSums();
    				epp.startSumsReconstruction();
    			}
    			ppThreadsBarrier.await();
    			if(!doOperations()){
    				logger.error("Reconstruction of the total sum (N) and the sum of the exponentiated item sums failed; returning");
    				return;
    			}
            }
			
			if (ppThreadsBarrier.await()==0) {
				privacyPeer.setFinalResult();
				logger.info("Entropy protocol round completed");
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
