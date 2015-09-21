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

package ch.ethz.sepia.protocolPrimitives.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.protocolPrimitives.Primitives;
import ch.ethz.sepia.mpc.protocolPrimitives.operationStates.GenericOperationState;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.IOperation;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.LessThan;
import ch.ethz.sepia.services.Services;
import ch.ethz.sepia.services.Utils;

/**
 * 
 * Class to test the MpcShamirSharingProtocolPrimitives class
 * 
 * @author Dilip Many
 * 
 */
public class OperationsTest extends TestCase {
    /** Logger to write log messages to */
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(OperationsTest.class));

    /**
     * the number of input values from the lower and upper end of the input
     * value range to use in the input
     */
    protected int borderSize = 6;
    /** the field size used in the Shamir sharing */
    protected long fieldSize = 1401085391;
    /** the field sizes used to test with */
    protected long[] fieldSizes = { 257, 1153, 4129, 12289, 32833, 65537,
            524353, 8650753, 9999991, 2147352577, 1111111111111111111L,
            3775874107000403461L, 9223372036854775783L };
    /** the input values */
    protected long[] input = null;
    /**
     * the Shamir bit shares of the input values; format:
     * [peerIndex][privacyPeerIndex][inputIndex][bitIndex]
     */
    protected long[][][][] inputBitShares = null;
    /**
     * the Shamir shares of the input values; format:
     * [peerIndex][privacyPeerIndex][inputIndex]
     */
    protected long[][][] inputShares = null;

    /**
     * MpcShamirSharing instances for the peers (to use basic operations on
     * Shamir shares)
     */
    protected ShamirSharing[] mpcShamirSharingPeers = null;
    /** the number of peers */
    protected int numberOfPeers = 3;
    /** the number of privacy peers */
    protected int numberOfPrivacyPeers = 5;
    /**
     * log entries of the operations; format: [privacyPeerIndex][operationIndex]
     */
    protected String[][] operationLogs = null;
    /** MpcShamirSharingProtocolPrimitives instances for the privacy peers */
    protected Primitives[] primitives = null;
    /** random number generator */
    protected Random random = null;
    /** name of random algorithm (used for Shamir sharing) */
    protected String randomAlgorithm = "SHA1PRNG";

    /** the number of random values to use in the input */
    protected int randomValuesCount = 10;

    /**
     * creates Shamir bit shares for the input values
     */
    protected void createInputBitShares() {
        final int bitsCount = this.primitives[0].getBitsCount();
        // create Shamir bit shares of input values
        String inputString = null;
        final long[] inputBits = new long[bitsCount];
        long[][] bitShares = null;
        this.inputBitShares = new long[this.numberOfPeers][this.numberOfPrivacyPeers][this.input.length][bitsCount];
        for (int peerIndex = 0; peerIndex < this.numberOfPeers; peerIndex++) {
            for (int inputIndex = 0; inputIndex < this.input.length; inputIndex++) {
                // get bits of input
                inputString = Long.toBinaryString(this.input[inputIndex]);
                // create leading zeroes
                int bitIndex = 0;
                if (inputString.length() < bitsCount) {
                    for (int j = inputString.length(); j < bitsCount; j++) {
                        inputBits[bitIndex] = 0;
                        bitIndex++;
                    }
                }
                for (int j = 0; j < inputString.length(); j++) {
                    if (inputString.charAt(j) == '0') {
                        inputBits[bitIndex] = 0;
                    } else {
                        inputBits[bitIndex] = 1;
                    }
                    bitIndex++;
                }
                bitShares = this.mpcShamirSharingPeers[peerIndex]
                        .generateShares(inputBits);
                // copy bit shares into inputBitShares array
                for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
                    this.inputBitShares[peerIndex][privacyPeerIndex][inputIndex] = new long[bitsCount];
                    System.arraycopy(
                            bitShares[privacyPeerIndex],
                            0,
                            this.inputBitShares[peerIndex][privacyPeerIndex][inputIndex],
                            0, bitsCount);
                }
            }
        }
    }

    /**
     * creates Shamir shares for the input values
     */
    protected void createInputShares() {
        // create Shamir shares of input values
        this.inputShares = new long[this.numberOfPeers][this.numberOfPrivacyPeers][this.input.length];
        for (int i = 0; i < this.numberOfPeers; i++) {
            this.inputShares[i] = this.mpcShamirSharingPeers[i]
                    .generateShares(this.input);
        }
    }

    protected void createInputValues() {
        // create input values
        if (2 * this.borderSize + this.randomValuesCount < this.fieldSize) {
            this.input = new long[2 * this.borderSize + this.randomValuesCount];
            // create lower end inputs
            for (int i = 0; i < this.borderSize; i++) {
                this.input[i] = i % this.fieldSize;
            }
            // create upper end inputs
            for (int i = 0; i < this.borderSize; i++) {
                this.input[this.borderSize + i] = (this.fieldSize - 1 - i)
                        % this.fieldSize;
            }
            // create random value inputs
            long randomValue = 0;
            for (int i = 0; i < this.randomValuesCount; i++) {
                randomValue = this.random.nextLong() % this.fieldSize;
                if (randomValue < 0) {
                    randomValue = 0 - randomValue;
                }
                this.input[2 * this.borderSize + i] = randomValue;
            }
        } else {
            // if the amount of requested inputs is larger than the group order
            // generate all possible input values
            this.input = new long[(int) this.fieldSize];
            for (int i = 0; i < this.fieldSize; i++) {
                this.input[i] = i;
            }
        }
    }

    /**
     * Same as {@link OperationsTest#doOperation(int[])}, but simulates the loss
     * of messages from a privacy peer. This tests the case where a privacy peer
     * goes offline during the computation.
     * 
     * @param operationIDs
     *            the ids of the operations to execute
     * @param failingPP
     *            the index of the failing PP
     * @param failForAll
     *            If set to <code>true</code>, the PP fails to deliver shares to
     *            all other privacy peers. Otherwise, each PP gets the shares
     *            with probability 50%. The later leads to inconsistent states
     *            where some privacy peers have shares from the failing PP and
     *            others don't!
     * @return
     */
    protected boolean doLossyOperation(final int[] operationIDs,
            final int failingPP, final boolean failForAll) {
        System.out.println("doLossyOperation will process "
                + this.primitives[0].getOperations().size() + " operations");

        generateRandomNumbersIfNeeded();

        int roundNumber = 1;
        boolean areOperationsCompleted = false;

        // process the initial data
        try {
            for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                this.primitives[i].processReceivedData();
            }
        } catch (final Exception e) {
            fail("An exception occured when processing the initial data: "
                    + Utils.getStackTrace(e));
        }
        // check if operations have completed already
        areOperationsCompleted = true;
        for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
            areOperationsCompleted = areOperationsCompleted
                    && this.primitives[i].areOperationsCompleted();
        }

        final Random rand = new Random();

        while (!areOperationsCompleted) {
            // send (copy) the data
            long[] dataToSend = null;
            for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                for (int j = 0; j < this.numberOfPrivacyPeers; j++) {
                    if (j != i) {
                        // "i sends shares to j" and "j receives shares from i"
                        final boolean failedTransmission = i == failingPP
                                && (failForAll || rand.nextBoolean());
                        if (failedTransmission) {
                            dataToSend = null;
                        } else {
                            dataToSend = this.primitives[i].getDataToSend(j);
                            assertNotNull(
                                    "data to send is null! (from privacy peer "
                                            + i + ", in round " + roundNumber
                                            + ")", dataToSend);
                            assertTrue("no data to send! (from privacy peer "
                                    + i + ", in round " + roundNumber + ")",
                                    dataToSend.length > 0);
                        }
                        this.primitives[j].setReceivedData(i, dataToSend);
                    }
                }
            }

            // check whether any suboperations with need for random numbers were
            // scheduled
            // if(operationIDs != null){
            // // only do the check when doOperations was not called from
            // generateRandomNumbersIfNeeded() itself
            // generateRandomNumbersIfNeeded();
            // }
            // process the data
            try {
                for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                    this.primitives[i].processReceivedData();
                }
            } catch (final Exception e) {
                fail("An exception occured when processing the received data: "
                        + Utils.getStackTrace(e));
            }

            // check if operations have completed yet
            areOperationsCompleted = true;
            for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                areOperationsCompleted = areOperationsCompleted
                        && this.primitives[i].areOperationsCompleted();
            }
            roundNumber++;
        }

        return true;
    }

    /**
     * executes the operations: simulates sending and receiving of shares,
     * processes the data and repeats if the operations havn't completed yet
     * 
     * @param operationIDs
     *            the ids of the operations to execute
     * @return
     */
    protected boolean doOperation(final int[] operationIDs) {
        System.out.println("doOperation will process "
                + this.primitives[0].getOperations().size() + " operations");

        generateRandomNumbersIfNeeded();

        int roundNumber = 1;
        boolean areOperationsCompleted = false;

        // process the initial data
        try {
            for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                this.primitives[i].processReceivedData();
            }
        } catch (final Exception e) {
            fail("An exception occured when processing the initial data: "
                    + Utils.getStackTrace(e));
        }
        // check if operations have completed already
        areOperationsCompleted = true;
        for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
            areOperationsCompleted = areOperationsCompleted
                    && this.primitives[i].areOperationsCompleted();
        }

        while (!areOperationsCompleted) {
            // send (copy) the data
            long[] dataToSend = null;
            for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                for (int j = 0; j < this.numberOfPrivacyPeers; j++) {
                    if (j != i) {
                        // "i sends shares to j" and "j receives shares from i"
                        dataToSend = this.primitives[i].getDataToSend(j);
                        assertNotNull(
                                "data to send is null! (from privacy peer " + i
                                        + ", in round " + roundNumber + ")",
                                dataToSend);
                        assertTrue("no data to send! (from privacy peer " + i
                                + ", in round " + roundNumber + ")",
                                dataToSend.length > 0);
                        this.primitives[j].setReceivedData(i, dataToSend);
                    }
                }
            }

            // check whether any suboperations with need for random numbers were
            // scheduled
            // if(operationIDs != null){
            // // only do the check when doOperations was not called from
            // generateRandomNumbersIfNeeded() itself
            // generateRandomNumbersIfNeeded();
            // }
            // process the data
            try {
                for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                    this.primitives[i].processReceivedData();
                }
            } catch (final Exception e) {
                fail("An exception occured when processing the received data: "
                        + Utils.getStackTrace(e));
            }

            // check if operations have completed yet
            areOperationsCompleted = true;
            for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
                areOperationsCompleted = areOperationsCompleted
                        && this.primitives[i].areOperationsCompleted();
            }
            roundNumber++;
        }

        return true;
    }

    /**
     * Checks whether current operations need bitwise shared random numbers and
     * generates them if needed.
     */
    protected void generateRandomNumbersIfNeeded() {
        long randomNumbersNeeded = 0;
        final int bitsPerElement = this.primitives[0].getBitsCount();
        final List<IOperation> ops = this.primitives[0].getOperations();

        // List<IOperation> opsWithRandomNeed = new ArrayList<IOperation>();
        for (int i = 0; i < ops.size(); i++) {
            // opsWithRandomNeed.addAll(recursiveGetOperationsWithRandomNumbersNeeded(ops.get(i)));
            if (ops.get(i) instanceof LessThan) {
                randomNumbersNeeded += ((LessThan) ops.get(i))
                        .getRandomNumbersNeeded(this.primitives[0]);
            }
        }

        // // Count random Numbers needed
        // for(int i=0; i<opsWithRandomNeed.size(); i++){
        // if (opsWithRandomNeed.get(i) instanceof LessThan) {// should always
        // be true
        // randomNumbersNeeded +=
        // ((LessThan)opsWithRandomNeed.get(i)).getRandomNumbersNeeded(primitives[0]);
        // }
        // }

        if (randomNumbersNeeded == 0) {
            return;
        }

        // Backup old operations and schedule random number generation
        for (int pp = 0; pp < this.numberOfPrivacyPeers; pp++) {
            this.primitives[pp].pushOperations();
            this.primitives[pp].initialize(1);
            this.primitives[pp].batchGenerateBitwiseRandomNumbers(0,
                    new long[] { randomNumbersNeeded });
        }

        // Perform random number generation
        doOperation(null);

        final long[][] preGeneratedRandomNumbers = new long[this.numberOfPrivacyPeers][];
        for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
            preGeneratedRandomNumbers[privacyPeerIndex] = this.primitives[privacyPeerIndex]
                    .getResult(0);
        }

        // Restore old operations and add the random numbers
        // for(int pp = 0; pp < numberOfPrivacyPeers; pp++) {
        // int bitIndex=0;
        // primitives[pp].popOperations(); // restore
        // for(int op=0; op<opsWithRandomNeed.size(); op++){
        // LessThan lt = (LessThan)opsWithRandomNeed.get(op);
        // int bitsNeeded =
        // lt.getRandomNumbersNeeded(primitives[pp])*bitsPerElement;
        // long [] bits = new long[bitsNeeded];
        // System.arraycopy(preGeneratedRandomNumbers[pp], bitIndex, bits, 0,
        // bitsNeeded);
        // lt.setRandomNumberBitShares(bits);
        // bitIndex += bitsNeeded;
        // }
        // }

        // Restore old operations and add the random numbers
        for (int pp = 0; pp < this.numberOfPrivacyPeers; pp++) {
            int bitIndex = 0;
            this.primitives[pp].popOperations();
            final List<IOperation> ppOps = this.primitives[pp].getOperations();
            for (int op = 0; op < ppOps.size(); op++) {
                if (ppOps.get(op) instanceof LessThan) {
                    // set random bits
                    final LessThan lt = (LessThan) ppOps.get(op);
                    final int bitsNeeded = lt
                            .getRandomNumbersNeeded(this.primitives[pp])
                            * bitsPerElement;
                    final long[] bits = new long[bitsNeeded];
                    System.arraycopy(preGeneratedRandomNumbers[pp], bitIndex,
                            bits, 0, bitsNeeded);
                    lt.setRandomNumberBitShares(bits);
                    bitIndex += bitsNeeded;
                }
            }
        }
    }

    protected void initializeMpcShamirSharingInstances() {
        // Choose the default of t=(m-1)/2.
        initializeMpcShamirSharingInstances(-1);
    }

    protected void initializeMpcShamirSharingInstances(final int degreeT) {
        // initialize MpcShamirSharing instances (for peers and privacy peers)
        ShamirSharing mpcShamirSharing = null;
        this.mpcShamirSharingPeers = new ShamirSharing[this.numberOfPeers];
        for (int i = 0; i < this.numberOfPeers; i++) {
            try {
                mpcShamirSharing = new ShamirSharing();
                mpcShamirSharing.setRandomAlgorithm(this.randomAlgorithm);
                mpcShamirSharing.setFieldSize(this.fieldSize);
                mpcShamirSharing
                        .setNumberOfPrivacyPeers(this.numberOfPrivacyPeers);
                mpcShamirSharing.setDegreeT(degreeT);
                mpcShamirSharing.init();
                this.mpcShamirSharingPeers[i] = mpcShamirSharing;
            } catch (final Exception e) {
                fail("An exception occured when creating and initializing a MpcShamirSharing instance: "
                        + Utils.getStackTrace(e));
            }
        }
        mpcShamirSharing = null;
    }

    protected void initializeMpcShamirSharingProtocolPrimitives() {
        initializeMpcShamirSharingProtocolPrimitives(-1);
    }

    protected void initializeMpcShamirSharingProtocolPrimitives(
            final int degreeT) {
        logger.info("numberOfPrivacyPeers := " + this.numberOfPrivacyPeers);
        // initialize MpcShamirSharingProtocolPrimitives for privacy peers
        Primitives primitive = null;
        this.primitives = new Primitives[this.numberOfPrivacyPeers];
        for (int i = 0; i < this.numberOfPrivacyPeers; i++) {
            try {
                primitive = new Primitives(this.randomAlgorithm,
                        this.fieldSize, degreeT, this.numberOfPrivacyPeers, i,
                        1);
            } catch (final Exception e) {
                fail("An exception occured when creating and initializing a MpcShamirSharingProtocolPrimitives instance: "
                        + Utils.getStackTrace(e));
            }
            this.primitives[i] = primitive;
        }
        primitive = null;
    }

    /**
     * parses the log entries of the specified operation (replacing shares by
     * the shared secret)
     * 
     * @param index
     *            the index of the operation for which to parse the logs
     */
    protected void parseLogs(final int index) {
        // next found shares: start, end, share of each privacy peer
        final int[][] position = new int[this.operationLogs.length][2];
        final long[] shares = new long[this.operationLogs.length];
        long result = 0;
        // while there still are shares in all logs
        while (true) {
            for (int privacyPeerIndex = 0; privacyPeerIndex < this.operationLogs.length; privacyPeerIndex++) {
                // extract next share from log
                position[privacyPeerIndex][0] = this.operationLogs[privacyPeerIndex][index]
                        .indexOf("<S>", position[privacyPeerIndex][1]);
                if (position[privacyPeerIndex][0] == -1) {
                    return;
                }
                position[privacyPeerIndex][1] = this.operationLogs[privacyPeerIndex][index]
                        .indexOf("</S>", position[privacyPeerIndex][0]);
                shares[privacyPeerIndex] = Long
                        .valueOf(this.operationLogs[privacyPeerIndex][index]
                                .substring(position[privacyPeerIndex][0] + 3,
                                        position[privacyPeerIndex][1]));
            }
            // reconstruct
            try {
                result = this.mpcShamirSharingPeers[0].interpolate(shares,
                        false);
            } catch (final Exception e) {
                logger.error("interpolation of shares failed: "
                        + Utils.getStackTrace(e));
            }
            // insert result into logs
            for (int privacyPeerIndex = 0; privacyPeerIndex < this.operationLogs.length; privacyPeerIndex++) {
                this.operationLogs[privacyPeerIndex][index] = this.operationLogs[privacyPeerIndex][index]
                        .replaceFirst(
                                "<S>" + shares[privacyPeerIndex] + "</S>",
                                result + " (share: " + shares[privacyPeerIndex]
                                        + ")");
            }
        }
    }

    protected List<IOperation> recursiveGetOperationsWithRandomNumbersNeeded(
            final IOperation operation) {
        final List<IOperation> opsWithNeed = new ArrayList<IOperation>();
        // long randomNumbersNeeded = 0;

        if (operation instanceof LessThan) {
            opsWithNeed.add(operation);
            // randomNumbersNeeded +=
            // ((LessThan)operation).getRandomNumbersNeeded(primitives[0]);
        }
        if (operation instanceof GenericOperationState) {
            final IOperation[] subs = ((GenericOperationState) operation)
                    .getSubOperations();
            if (subs != null) {
                for (IOperation sub : subs) {
                    opsWithNeed
                            .addAll(recursiveGetOperationsWithRandomNumbersNeeded(sub));
                    // randomNumbersNeeded += ;
                }
            }
        }
        return opsWithNeed;
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        this.random = new Random();
    }

    /*
     * (non-Javadoc)
     * 
     * @see junit.framework.TestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * retrieves the logs, parses them and saves them to a file
     * 
     * @param operationIDs
     *            the ids of the operations of which to write the logs
     */
    protected void writeLogs(final int[] operationIDs) {
        this.operationLogs = new String[this.numberOfPrivacyPeers][operationIDs.length];
        for (int i = 0; i < operationIDs.length; i++) {
            for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
                this.operationLogs[privacyPeerIndex][i] = this.primitives[privacyPeerIndex]
                        .getLog(operationIDs[i]);
            }
            parseLogs(i);
        }
        for (int privacyPeerIndex = 0; privacyPeerIndex < this.numberOfPrivacyPeers; privacyPeerIndex++) {
            String log = new String();
            for (int i = 0; i < this.operationLogs[privacyPeerIndex].length; i++) {
                log += "\n\n" + this.operationLogs[privacyPeerIndex][i];
            }
            try {
                Services.writeFile(log, "operation-logs__fs-" + this.fieldSize
                        + "_pp-" + privacyPeerIndex + ".txt");
            } catch (final Exception e) {
                System.out.println("thread " + Thread.currentThread().getId()
                        + " failed to write file: " + Utils.getStackTrace(e));
            }
        }
    }
}
