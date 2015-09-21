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

package ch.ethz.sepia.mpc.uniqueCount;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.sepia.connections.PrivacyViolationException;
import ch.ethz.sepia.services.Stopper;
import ch.ethz.sepia.services.Utils;

/**
 * A UniqueCount protocol between a peer and a privacy peer.
 *
 * @author Dilip Many
 *
 */
public class UniqueCountProtocolPeer extends UniqueCountProtocol {
    private static final Logger logger = LogManager.getLogger(UniqueCountProtocolPeer.class);

	/** reference to the uniquecount peer object that started this protocol instance */
	private UniqueCountPeer inputPeer;
	private int privacyPeerIndex;


	/**
	 * Creates a new instance of a protocol between a peer and a privacy peer.
	 *
	 * @param threadNumber		Protocol's thread number
	 * @param inputPeer		Peer who started the protocol
	 * @param cm 				connection manager
	 * @param privacyPeerID		ID of the communication counterpart
	 * @param stopper			Stopper to stop protocol thread
	 */
	public UniqueCountProtocolPeer(int threadNumber, UniqueCountPeer inputPeer, String privacyPeerID, int privacyPeerIndex, Stopper stopper) {
		super(threadNumber, inputPeer, privacyPeerID, privacyPeerIndex, stopper);
		this.inputPeer = inputPeer;
		this.privacyPeerIndex = privacyPeerIndex;
	}


	/**
     * Run the UniqueCount protocol for the peer
     * 
     * One round of communication looks as follows:
     * <ul>
     * <li>Send Shares
     * <li>Receive Final Result (zeros if peer was disqualified)
     * </ul> 
     */
    public void run() {
		// Send the initial shares
		createInitialSharesMessage();
		try {
			sendMessage();
		} catch (PrivacyViolationException e) {
			logger.error(Utils.getStackTrace(e));
			return;
		}
		logger.info("Sent initial shares.");

		// wait for final result
		logger.info("Waiting for final result");
		try {
			receiveMessage();
		} catch (PrivacyViolationException e) {
			logger.error(Utils.getStackTrace(e));
			return;
		}
    }


	/**
	 * Create the first messages with which the initial shares are sent to the
	 * other peers.
	 */
	private synchronized void createInitialSharesMessage() {
		logger.info("Creating message for first round (send initial shares)");
		inputPeer.generateInitialShares();
		messageToSend = new UniqueCountMessage(inputPeer.getMyPeerID(), myPeerIndex);
		messageToSend.setSenderIndex(myPeerIndex);
		messageToSend.setTimeSlotCount(timeSlotCount);
		messageToSend.setMetricCount(metricCount);
		messageToSend.setIsInitialSharesMessage(true);
		messageToSend.setShares(inputPeer.getInitialSharesForPrivacyPeer(privacyPeerIndex));
	}
}
