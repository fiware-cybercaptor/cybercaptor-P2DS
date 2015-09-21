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

package ch.ethz.sepia.mpc;

import java.util.Observable;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.services.Stopper;

/**
 * A general MPC protocol class.
 *
 * @author Lisa Barisic, ETH Zurich
 */
public abstract class ProtocolBase extends Observable implements Runnable {
    protected static final int DEFAULT_SLEEPTIME = 1500;
    private static final XLogger logger = new XLogger(LoggerFactory.getLogger(ProtocolBase.class));
    protected ConnectionManager connectionManager;

    protected boolean goodbyesDone = false;
    protected int metricCount;
    /** my ID */
    protected String myPeerID = null;
    protected int myPeerIndex = 0;
    protected int myThreadNumber = 0;
    protected int numberOfParticipants;
    /** The ID of my counterpart */
    protected String otherPeerID = null;
    protected int otherPeerIndex = -1;
    protected Stopper stopper = null;
    protected int timeSlotCount;

    /**
     * Creates a new peer that shares data (connected to one other peer)
     *
     * @param threadNumber This peer's thread number (for identification when
     *                     notifying observers)
     *
     * @param cm the connection manager
     * @param myPeerID The local peer's own ID (to be sent to other peers)
     * @param otherPeerID the other peer's ID
     * @param myPeerIndex The peer's own number/index (to be sent to other peers)
     * @param otherPeerIndex The other peer's number/index
     * @param stopper the stopper
     */
    public ProtocolBase(final int threadNumber, final ConnectionManager cm, final String myPeerID, final String otherPeerID, final int myPeerIndex, final int otherPeerIndex,final Stopper stopper) {
        this.myThreadNumber = threadNumber;
        this.connectionManager = cm;
        this.myPeerID = myPeerID;
        this.otherPeerID = otherPeerID;
        this.myPeerIndex = myPeerIndex;
        this.otherPeerIndex = otherPeerIndex;
        this.stopper = stopper;
    }

    /**
	 * @return the ID of the peer which started this protocol instance
	 */
	public String getMyPeerID() {
		return myPeerID;
	}

    public int getMyPeerIndex() {
        return myPeerIndex;
    }

    protected void initialize(final int timeSlotCount, final int metricCount, final int numberOfParticipants) {
        this.timeSlotCount = timeSlotCount;
        this.metricCount = metricCount;
        this.numberOfParticipants = numberOfParticipants;
    }

    /**
     * Sets changed and then notifies the observers
     */
    protected synchronized void notify(final Object object) {
        logger.info("Notifying observers");
        setChanged();
        notifyObservers(object);
    }

    protected abstract void receiveMessage() throws Exception;

    protected abstract void sendMessage() throws Exception;

	public void setMyPeerIndex(final int myPeerIndex) {
        this.myPeerIndex = myPeerIndex;
    }
}


