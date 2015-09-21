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

package ch.ethz.sepia.mpc.protocolPrimitives;

import java.util.Observer;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.connections.ConnectionManager;
import ch.ethz.sepia.mpc.PeerBase;
import ch.ethz.sepia.services.Stopper;

/**
 * Abstract class for concrete input and privacy peer classes that want to use
 * the operations from {@link Primitives}.
 * <p>
 * Before using the operations through this class the number of privacy peers (
 * {@link #numberOfPrivacyPeers}) involved in the operations has to be set.
 * (Otherwise synchronization problems will occur!)
 * <p>
 * Note: It's not mandatory to inherit from this class to use the
 * {@link Primitives} class. It just makes things easier.
 * 
 * @author Dilip Many
 * 
 */
public abstract class PrimitivesEnabledPeer extends PeerBase implements
        Observer {
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(PrimitivesEnabledPeer.class));

    /** Counting Barrier instance to synchronize PP-to-PP protocol threads */
    private CyclicBarrier barrierPP2PPProtocolThreads = null;
    /** identification number of the current set of operations */
    private int currentOperationSetNumber = 0;
    /** the alpha index of this (privacy) peer */
    protected int myAlphaIndex = 0;

    /** number of privacy peers participating in the computation */
    protected int numberOfPrivacyPeers = 0;
    /** identifiers of operations which are currently in progress */
    protected int[] operationIDs = null;

    /** MpcShamirSharingProtocolPrimitives instance to use basic operations */
    protected Primitives primitives = null;

    /**
     * creates a new PrimitivesEnabledPeer instance
     * 
     * @param myPeerIndex
     *            the index of this privacy peer
     * @param cm
     *            the connection manager
     * @param stopper
     *            Stopper (can be used to stop this thread)
     */
    public PrimitivesEnabledPeer(final String myPeerName,
            final int myPeerIndex, final ConnectionManager cm,
            final Stopper stopper) {
        super(myPeerName, myPeerIndex, cm, stopper);
    }

    /**
     * Resets the PP2PP barrier. When it is requested the next time, it is
     * initialized with the current number of connected privacy peers.
     */
    public void clearPP2PPBarrier() {
        this.barrierPP2PPProtocolThreads = null;
    }

    /**
     * @return the barrier (for the PP-to-PP protocol threads)
     */
    public CyclicBarrier getBarrierPP2PPProtocolThreads() {
        if (this.barrierPP2PPProtocolThreads == null) {
            initPP2PPBarrier();
        }
        return this.barrierPP2PPProtocolThreads;
    }

    /**
     * returns the current operation set number
     * 
     * @return the current operation set number
     */
    public int getCurrentOperationSetNumber() {
        return this.currentOperationSetNumber;
    }

    /**
     * returns the ids of the currently running operations
     * 
     * @return the ids of the currently running operations
     */
    public int[] getOperationIDs() {
        return this.operationIDs;
    }

    /**
     * returns a reference to the MpcShamirSharingProtocolPrimitives instance
     */
    public Primitives getPrimitives() {
        return this.primitives;
    }

    /**
     * initializes all variables for new operation set: resets the operation
     * round variables (used for synchronization), the operation states and the
     * operation IDs, initializes the protocol primitives to run all operations
     * of the entire operation set in parallel
     * 
     * (the caller has to make sure that this function is only called once per
     * operation set!)
     * 
     * @param operationsCount
     *            the number of operations that will be added to this set of
     *            operations
     */
    public synchronized void initializeNewOperationSet(final int operationsCount) {
        this.primitives.initialize(operationsCount);
        this.currentOperationSetNumber++;
        logger.info("thread " + Thread.currentThread().getId()
                + " initialized new operation set "
                + this.currentOperationSetNumber);
        if (this.operationIDs != null) {
            this.operationIDs = null;
        }
    }

    /**
     * initializes all variables for new operation set and initializes the
     * protocol primitives to run the specified amount of operations in parallel
     * 
     * (the caller has to make sure that this function is only called once per
     * operation set!)
     * 
     * @param parallelOperationsCount
     *            the number of operations that shall be run in parallel (0 if
     *            all operations of the set shall be run in parallel)
     * @param totalOperationsCount
     *            the total number of operations that will be added to this set
     *            of operations
     */
    public synchronized void initializeNewOperationSet(
            final int parallelOperationsCount, final int totalOperationsCount) {
        if (parallelOperationsCount == 0) {
            logger.info("parallelOperationsCount = 0; => will run all "
                    + totalOperationsCount + " in parallel");
            initializeNewOperationSet(totalOperationsCount);
            return;
        }
        this.primitives.initialize(parallelOperationsCount,
                totalOperationsCount);
        this.currentOperationSetNumber++;
        logger.info("thread " + Thread.currentThread().getId()
                + " initialized new operation set "
                + this.currentOperationSetNumber);
        if (this.operationIDs != null) {
            this.operationIDs = null;
        }
    }

    private synchronized void initPP2PPBarrier() {
        if (this.barrierPP2PPProtocolThreads == null) {
            if (this.numberOfPrivacyPeers < 2) {
                logger.error("the number of privacy peers ("
                        + this.numberOfPrivacyPeers
                        + ") seems to be uninitialized!!!");
            }
            this.barrierPP2PPProtocolThreads = new CyclicBarrier(
                    this.numberOfPrivacyPeers - 1);
            // logger.info("created SimpleCountingBarrier to synchronize the "+(numberOfPrivacyPeers-1)+" protocol threads");
        }
    }

    /**
     * processes Shamir Sharing Protocol Primitives messages
     */
    public void processShamirSharingProtocolPrimitivesMessage(
            final PrimitivesMessage mpcPrimitivesMessage) {
        final int senderPrivacyPeerIndex = mpcPrimitivesMessage
                .getSenderIndex();
        final long[] data = mpcPrimitivesMessage.getOperationsData();

        logger.info("setting data of operations from: "
                + senderPrivacyPeerIndex);
        this.primitives.setReceivedData(senderPrivacyPeerIndex, data);
    }

}
