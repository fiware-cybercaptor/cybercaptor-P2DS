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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.ArrayEqual;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.ArrayMultiplication;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.ArrayPower;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.ArrayProduct;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BatchGenerateBitwiseRandomNumbers;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BitwiseLessThan;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BloomFilterCardinality;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BloomFilterIntersection;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BloomFilterThresholdUnion;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BloomFilterUnion;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.BloomFilterWeightedIntersection;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Equal;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.GenerateBitwiseRandomNumber;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.GenerateRandomBit;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.GenerateRandomNumber;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.IOperation;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.LeastSignificantBit;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.LessThan;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.LinearPrefixOr;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Min;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Multiplication;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Power;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Product;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Reconstruction;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.SmallIntervalTest;
import ch.ethz.sepia.mpc.protocolPrimitives.operations.Synchronization;

/**
 * Class offering basic operations for creating MPC protocols using Shamir
 * shares.
 * <p/>
 * When used from SEPIA input and privacy peers, it is recommended that this
 * class is used via the helper classes
 * {@link mpc.protocolPrimitives.PrimitivesEnabledProtocol} and
 * {@link mpc.protocolPrimitives.PrimitivesEnabledPeer}. These classes already
 * implement recurring SEPIA-specific initialization and synchronization tasks,
 * e.g., the synchronization of intermediate value shares among the privacy
 * peers.
 * <p/>
 * If this class is used without the helper classes, make sure to call
 * {@link #initialize(int)} or {@link #initialize(int, int)} before creating and
 * running a set of operations. Here is some example code how to use this class.
 * Three input peers have a value and the privacy peers compute which of these
 * are equal without learning the values:
 * 
 * <p/>
 * <b>Input peer 1 (other input peers do the same)</b>
 * 
 * <pre>
 * {@code
 * //Creation of shares
 * ShamirSharing sharing = new ShamirSharing();
 * ... // initialize the sharing with several parameters
 * 
 * long[] secrets = new long[]{123456}; // Input1: just one value
 * long[][] inputShares = sharing.generateShares(long[] secrets);
 * 
 * ... // send long[i] to privacy peer i
 * }
 * </pre>
 * 
 * <b>Privacy peers</b>
 * 
 * <pre>
 * ... // receive all the shares from the input peers
 * Primitives primitives = new Primitives(randomAlgorithm, fieldSize, ...);
 * 
 * // Scheduling equality checks of all the input peer's input values
 * primitives.equal(1, new long[]{shareOfInput1, shareOfInput2});
 * primitives.equal(2, new long[]{shareOfInput2, shareOfInput3});
 * primitives.equal(3, new long[]{shareOfInput1, shareOfInput3});
 * 
 * // Processing operations
 * primitives.processReceivedData();  // Start processing
 * while(!primitives.areOperationsCompleted()) {
 *     // Synchronize the intermediate shares with other privacy peers using the
 *     // methods {@link #getDataToSend(int)} and {@link #setReceivedData(int, long[])}.
 *     ...
 *     primitives.processReceivedData();
 * }
 * 
 * // Get shares of the comparison results
 * long shareOfEqual12 = primitives.getResult(1)[0];
 * long shareOfEqual23 = primitives.getResult(2)[0];
 * long shareOfEqual13 = primitives.getResult(3)[0];
 * 
 * // Scheduling reconstruction
 * primitives.reconstruct(1, new long[]{shareOfEqual12});
 * primitives.reconstruct(2, new long[]{shareOfEqual23});
 * primitives.reconstruct(3, new long[]{shareOfEqual13});
 * 
 * // Running reconstruction
 * primitives.processReceivedData();
 * while(!primitives.areOperationsCompleted()) {
 *     ... // Synchronization (see above)
 *     primitives.processReceivedData();
 * }
 * 
 * System.out.println("Input1 == Input2 : "+ (primitives.getResult(1)[0]==1) ? "true" : "false");
 * System.out.println("Input2 == Input3 : "+ (primitives.getResult(2)[0]==1) ? "true" : "false");
 * System.out.println("Input1 == Input3 : "+ (primitives.getResult(3)[0]==1) ? "true" : "false");
 * </pre>
 * 
 * @author Dilip Many
 * 
 */
public class Primitives {

    /** Logger to write log messages to */
    private static final XLogger logger = new XLogger(
            LoggerFactory.getLogger(Primitives.class));

    /** Barrier instance to synchronize threads */
    private CyclicBarrier barrier = null;

    /** the number of bits used to represent the field size */
    private int bitsCount = 0;
    /** the number of completed operations */
    private int completedOperationsCount = 0;
    /** the id of the operation which is currently processed */
    private final int currentOperationID = -1;

    /** the size of the finite field used for the Shamir sharing */
    private long fieldSize = 0;
    /** MpcShamirSharing instance to use basic operations on Shamir shares */
    private ShamirSharing mpcShamirSharing = null;
    /** the index of the privacy peer using this object instance */
    private int myPrivacyPeerIndex = 0;

    /** the number of privacy peers */
    private int numberOfPrivacyPeers = 0;

    /** variables of debugging functions */
    /** stores the descriptions of the operations for which logs shall be kept */
    private final ArrayList<String> operationDescriptions = null;
    /** stores the logs of the operations */
    private final ArrayList<String> operationLogs = null;
    /** stores the running operations */
    private ArrayList<IOperation> operations = null;
    /** stores the IDs of the running operations */
    private ArrayList<Integer> operationsIDs = null;
    /** stores the queued operations */
    private ArrayList<IOperation> operationsQueue = null;
    /** stores the results of the operations */
    private long[][] operationsResults = null;
    /** the number of operations that shall be run in parallel */
    private int parallelOperationsCount = 0;
    /** used for caching intermediate results of operations */
    private final Hashtable<Object, Long> predicateCache;

    /** the number of protocol threads that execute the MPC computations */
    private int protocolThreadsCount = 0;
    /** (local) random number generator instance */
    private Random random = null;
    /**
     * This stack is only used in push/popOperations to save/restore the current
     * state.
     */
    private Stack<Object> stateVariables = null;

    /**
     * the total number of operations that will be added to this set of
     * operations
     */
    private int totalOperationsCount = 0;

    /**
     * creates a new MpcShamirSharingProtocolPrimitives instance
     * 
     * @param randomAlgorithm
     *            the random algorithm to use in the Shamir sharing
     * @param fieldSize
     *            the field size used in the Shamir sharing
     * @param degreeT
     *            the degree of the polynomials used. Set to -1 for the default
     *            t=(m-1)/2.
     * @param numberOfPrivacyPeers
     *            the total number of privacy peers involved in the computations
     * @param myPrivacyPeerIndex
     *            the index of the privacy peer using this object instance
     * @param protocolThreadsCount
     *            the number of threads involved in the protocol execution
     */
    public Primitives(final String randomAlgorithm, final long fieldSize,
            final int degreeT, final int numberOfPrivacyPeers,
            final int myPrivacyPeerIndex, final int protocolThreadsCount) {
        this.numberOfPrivacyPeers = numberOfPrivacyPeers;
        this.myPrivacyPeerIndex = myPrivacyPeerIndex;
        this.fieldSize = fieldSize;
        this.bitsCount = Long.toBinaryString(fieldSize).length();

        // initialize MpcShamirSharing instance
        this.mpcShamirSharing = new ShamirSharing();
        this.mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
        this.mpcShamirSharing.setFieldSize(fieldSize);
        logger.info("numberOfPrivacyPeers := " + numberOfPrivacyPeers);
        this.mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
        this.mpcShamirSharing.setDegreeT(degreeT);
        this.mpcShamirSharing.init();

        this.protocolThreadsCount = protocolThreadsCount;
        this.barrier = new CyclicBarrier(protocolThreadsCount);

        this.random = new Random();

        this.stateVariables = new Stack<Object>();
        this.predicateCache = new Hashtable<Object, Long>();
    }

    /**
     * allows to check if all operations are completed
     * 
     * @return true if all the operations are completed
     */
    public synchronized boolean areOperationsCompleted() {
        return this.completedOperationsCount >= this.totalOperationsCount;
    }

    public boolean arrayEqual(final int id, final long[] data1,
            final long[] data2) {
        if (data1.length != data2.length) {
            logger.warn("creation of arrayEqual operation needs arrays of equal length !");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new ArrayEqual(data1, data2));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new ArrayEqual(data1, data2));
        }

        return true;
    }

    /**
     * Multiplies two arrays position wise. E.g. if A = [1,2,3,4,5] and B =
     * [5,4,3,2,1] arraymult( a,b ) = [1*5, 2*4, 3*3, 4*2, 5*1] the two arrays
     * must have equal length.
     * 
     * @param id
     *            id of the operation
     * @param factor1
     *            the first factor of the multiplication
     * @param factor2
     *            the second factor of the multiplication
     * @return true if the operation was created successfully
     */
    public boolean arrayMult(final int id, final long[] factor1,
            final long[] factor2) {
        if (factor1.length != factor2.length) {
            logger.warn("creation of arraymult operation needs arrays of equal length !");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new ArrayMultiplication(factor1, factor2));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new ArrayMultiplication(factor1, factor2));
        }

        return true;
    }

    public boolean arrayPower(final int id, final long[] data,
            final long exponent) {
        if (data.length < 1) {
            logger.warn("creation of arrayPower operation needs at least 1 item !");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new ArrayPower(data, exponent));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new ArrayPower(data, exponent));
        }

        return true;
    }

    /**
     * Same as the Product Operation but for Arrays. This operation multiplies
     * in each step the factors with even index and odd index: a_1=[x,x,...]
     * a_2=[y,y,...] a_3=[z,z,...] a_4=[...]
     * 
     * a_12 = a_1 .* a_2 a_34 = a_3 .* a_4
     * 
     * final = a_12 .* a_34
     * 
     * All arrays must be of same length. Inputformat: [ArrayIndex][dataIndex]
     * 
     * This process is then repeated until there is only 1 result which then is
     * the final result.
     * 
     * There is also the possiblity to do only one ArrayMultiplication at a time
     * in this case the result is computed as: res = a_1 * a_2; res = res * a_3;
     * res = res * a_4; ...
     * 
     * The same number of multiplications is needed, but instead of log(n)
     * rounds this needs n rounds.
     * 
     * In general if the arrays are large sequential ArrayMultiplications (more
     * rounds) perform better than the round optimized variant, since the memory
     * is used more efficiently and the computational load on privacy peers is
     * equal in every round.
     * 
     * Performance measurments showed that memory savings of up to 50% are
     * possible and the running time was also reduce by factor 2 (Array.length
     * was 2^20). But for small arrays setting the option to true might be
     * faster.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            arrays to mulitply, format: [ArrayIndex][dataIndex]
     * @param fewRounds
     *            if true more multiplications are done per round and fewer
     *            rounds are needed.
     * @return true if the operation was created successfully
     */
    public boolean arrayProduct(final int id, final long[][] data,
            final boolean fewRounds) {
        if (data.length < 2) {
            logger.warn("creation of arrayProduct operation needs at least length 2 in 1st dimension!");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new ArrayProduct(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new ArrayProduct(data));
        }

        return true;
    }

    /**
     * generates (at least) the specified amount of bitwise shared random
     * numbers
     * <p>
     * The running time of this operation might vary as it won't terminate until
     * it has successfully generated the requested amount of bitwise shared
     * random numbers.
     * <p>
     * This operation can generate at most Integer.MAX_VALUE/bitsCount numbers.
     * The field size has to be at least 3 for this operation to work.
     * <p>
     * The final result are the bit shares of the random numbers.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            the number of bitwise random numbers to generate
     * @return true if the operation was created successfully
     */
    public boolean batchGenerateBitwiseRandomNumbers(final int id,
            final long[] data) {
        // check input arguments
        if (data != null) {
            if (data.length != 1) {
                logger.warn("creation of batch generate bitwise (shared) random numbers operation takes one argument!");
                return false;
            }
            if (data[0] < 1 || data[0] * this.bitsCount > Integer.MAX_VALUE) {
                logger.warn("requested amount of bitwise shared random numbers must be between 1 and "
                        + (long) Integer.MAX_VALUE
                        / this.bitsCount
                        + "! (received: " + data[0] + ")");
                return false;
            }
        }

        // create and store batch generate bitwise (shared) random numbers
        // operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BatchGenerateBitwiseRandomNumbers(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BatchGenerateBitwiseRandomNumbers(data));
        }

        return true;
    }

    /**
     * Returns the number of bits set to 1 or the sum of all counters of
     * BloomFilter based sets. There is no need to specify whether it is a
     * counting or a non-counting filter, since the sum is computed in either
     * case.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            shares of the Bloom filter
     * @return true if the operation was created successfully
     */
    public boolean bfCardinality(final int id, final long[] data) {
        if (data.length < 1) {
            logger.warn("creation of bfCardinality operation needs at least 1 share!");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BloomFilterCardinality(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BloomFilterCardinality(data));
        }
        return true;
    }

    /**
     * Computes the intersection of BloomFilter based sets. In case of
     * non-counting BloomFilter the intersection is defined as the bitwise AND
     * of all filters at every filterposition. For the counting BloomFilter the
     * minimum function is computed
     * 
     * @param id
     *            id of the operation
     * @param data
     *            shares of the Bloom filters, format: [Filter X][Position i]
     * @param isCounting
     *            true if it is a counting filter or false else
     * @return true if the operation was created successfully
     */
    public boolean bfIntersection(final int id, final long[][] data,
            final boolean isCounting) {
        if (data.length < 2) {
            logger.warn("creation of bfIntersection operation needs at least 2 filters!");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BloomFilterIntersection(data, isCounting));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BloomFilterIntersection(data, isCounting));
        }
        return true;
    }

    /**
     * Computes the threshold union of BloomFilter based sets.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            shares of the Bloom filters, format: [Filter X][Position i]
     * @param T
     *            the threshold
     * @param learnCount
     *            depending on this, the result is either a counting or a
     *            noncounting BloomFilter
     * @return true if the operation was created successfully
     */
    public boolean bfThresholdUnion(final int id, final long[][] data,
            final long T, final boolean learnCount) {
        if (data.length < 2) {
            logger.warn("creation of bfThresholdUnion operation needs at least 2 BloomFilters!");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BloomFilterThresholdUnion(data, T,
                    learnCount));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BloomFilterThresholdUnion(data, T, learnCount));
        }
        return true;
    }

    /**
     * Computes the union of BloomFilter based sets. In case of non-counting
     * BloomFilter the union is defined as the bitwise OR of all filters at
     * every filterposition. For the counting BloomFilter the sum is computed
     * 
     * @param id
     *            id of the operation
     * @param data
     *            shares of the Bloom filters, format: [Filter X][Position i]
     * @param isCounting
     *            true if it is a counting filter or false else
     * @return true if the operation was created successfully
     */
    public boolean bfUnion(final int id, final long[][] data,
            final boolean isCounting) {
        if (data.length < 2) {
            logger.warn("creation of bfUnion operation needs at least 2 filters!");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BloomFilterUnion(data, isCounting));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BloomFilterIntersection(data, isCounting));
        }
        return true;
    }

    /**
     * Computes the threshold union of BloomFilter based sets.
     * 
     * @param id
     *            id of the operation
     * @param Keys
     *            shares of the key Bloom filters, format: [Filter X][Position
     *            i]
     * @param Weights
     *            shares of the weight Bloom filters, format: [Filter
     *            X][Position i]
     * @param Tk
     *            the threshold for the Keys
     * @param Tw
     *            the threshold for the Weights
     * @param learnCount
     *            depending on this, the result is either a counting or a
     *            noncounting BloomFilter
     * @return true if the operation was created successfully
     */
    public boolean bfWeightedSetIntersection(final int id, final long[][] Keys,
            final long[][] Weights, final long Tk, final long Tw,
            final boolean learnWeight) {
        if (Keys.length < 2 || Weights.length < 2) {
            logger.warn("creation of bfWeightedSetInterseciton operation needs at least 2 BloomFilters"
                    + "for the Keys and the Weights respectively.");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BloomFilterWeightedIntersection(Keys,
                    Weights, Tk, Tw, learnWeight));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BloomFilterWeightedIntersection(Keys, Weights, Tk, Tw,
                            learnWeight));
        }
        return true;
    }

    /**
     * computes the less-than (a &lt; b) of two (bitwise shared) numbers
     * <p>
     * a or b can also be publicly known numbers. data[0] just has to be set
     * accordingly:
     * <li>0: if both values are bitwise shared
     * <li>1: if a is a publicly known value and b is bitwise shared
     * <li>2: if a is bitwise shared and b is a publicly known value
     * <p>
     * Let z be the number of bits used for the binary representation of the
     * largest element of the field. Then data[1] up to data[z] must contain the
     * bits of a and data[z+1] up to data[2*z] must contain the bits of b, where
     * data[1] (z+1) contains the most significant bit of a (b) and data[z]
     * (2*z) the least significant.
     * <p>
     * To compute the bits of a publicly known value use {@link #getBits(long)}.
     * <p>
     * After the operation is completed the final result holds a share of one if
     * a &lt; b and a share of zero otherwise.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            operation type and the bit (shares) of the values
     * @return true if the operation was created successfully
     */
    public boolean bitwiseLessThan(final int id, final long[] data) {
        // check input arguments
        if (data == null) {
            logger.warn("Creation of bitwise less-than operation failed! No arguments were specified!");
            return false;
        }
        if (data.length - 1 != 2 * this.bitsCount) {
            logger.warn("Incorrect amount of arguments: expected="
                    + (1 + 2 * this.bitsCount) + ", received=" + data.length);
            return false;
        }
        if (!(data[0] == 0 || data[0] == 1 || data[0] == 2)) {
            logger.warn("Incorrect type: must be 0,1, or 2; received="
                    + data[0]);
            return false;
        }

        // create and store bitwise less-than operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new BitwiseLessThan(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new BitwiseLessThan(data));
        }

        return true;
    }

    /**
     * computes the number (share) from the bit (shares)
     * <p>
     * This function works for shared and public numbers.
     * 
     * @param bits
     *            the bit (shares) to compute the number of
     * @return the computed number (share)
     */
    public long computeNumber(final long[] bits) {
        long result = 0;
        for (int i = 0; i < this.bitsCount; i++) {
            result = this.mpcShamirSharing.modAdd(result, this.mpcShamirSharing
                    .modMultiply(bits[i], 1L << this.bitsCount - i - 1));
        }
        return result;
    }

    /**
     * The following functions are intended to debug operations.
     * 
     * For these function to work uncomment the lines in {@link #initialize} and
     * {@link #processReceivedData}.
     */

    /**
     * tests two secrets for equality
     * <p>
     * The final result is a share of one if the input secrets are equal and a
     * share of zero if the input secrets are not equal.
     * <p>
     * Note: This operation only works if the fieldSize is a prime! Use
     * {@link #setOptimalFieldSizeForEqual(long)} to set the optimal field size,
     * s.t. this operation runs as fast as possible.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing the 2 shares to test for equality
     * @return true if operation arguments are valid
     */
    public boolean equal(final int id, final long[] data) {
        // check number of input arguments
        if (data.length != 2) {
            logger.warn("creation of equal operation failed: invalid number of arguments!");
            return false;
        }

        // create and store equal operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Equal(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Equal(data));
        }

        return true;
    }

    /**
     * generates a bitwise shared random number
     * <p>
     * This operation <b>might fail</b> with a probability of
     * 1-((fieldSize-1)/fieldSize)^log(fieldSize) +
     * 1-(fieldSize/(2^log(fieldSize)))
     * <p>
     * If the operation receives random bit shares as arguments it tries to
     * compute the bitwise shared random number with these. The operation might
     * still fail with probability 1-(fieldSize/(2^log(fieldSize)))
     * <p>
     * The final result are the bit shares of the random number (if successful).
     * The share of the random number can be computed from the bit shares using:
     * {@link #computeNumber(long[])} In case the operation failed the final
     * result is -1.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            null or the random bit shares that shall be used to compute
     *            the bitwise shared random number
     * @return true if the operation was created successfully
     */
    public boolean generateBitwiseRandomNumber(final int id, final long[] data) {
        // check input arguments
        if (data != null) {
            if (data.length != this.bitsCount) {
                logger.warn("creation of bitwise (shared) random number generation operation takes either none or "
                        + this.bitsCount + " arguments!");
                return false;
            }
        }

        // create and store bitwise (shared) random number generation operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new GenerateBitwiseRandomNumber(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new GenerateBitwiseRandomNumber(data));
        }

        return true;
    }

    /**
     * generates a shared random bit (0 or 1)
     * <p>
     * The shared random bit generation <b>may fail</b> with probability
     * 1/fieldSize. In case of failure the final result is set to -1.
     * <p>
     * Notice: The share of the random bit can be any value of the field. Only
     * the shared random bit is either 0 or 1.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            null
     * @return true if the operation was created successfully
     */
    public boolean generateRandomBit(final int id, final long[] data) {
        // check input arguments
        if (data != null) {
            logger.warn("creation of random bit generation operation takes no arguments!");
            return false;
        }

        // create and store random bit generation operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new GenerateRandomBit(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new GenerateRandomBit(data));
        }

        return true;
    }

    /**
     * generates a shared random number
     * 
     * @param id
     *            id of the operation
     * @param data
     *            null
     * @return true if operation was created successfully
     */
    public boolean generateRandomNumber(final int id, final long[] data) {
        // check input arguments
        if (data != null) {
            logger.warn("creation of random number generation operation takes no arguments!");
            return false;
        }

        // create and store random number generation operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new GenerateRandomNumber(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new GenerateRandomNumber(data));
        }

        return true;
    }

    /**
     * returns the bits of the specified (public) value in an array (where
     * array[0] is the most significant bit)
     * 
     * @param value
     *            the value of which to retrieve the bits
     * @return the bits of the value
     */
    public long[] getBits(final long value) {
        final String inputString = Long.toBinaryString(value);
        final long[] result = new long[this.bitsCount];
        // create leading zeroes
        int bitIndex = 0;
        if (inputString.length() < this.bitsCount) {
            for (int j = inputString.length(); j < this.bitsCount; j++) {
                result[bitIndex] = 0;
                bitIndex++;
            }
        }
        // create rest
        for (int i = 0; i < inputString.length(); i++) {
            if (inputString.charAt(i) == '0') {
                result[bitIndex] = 0;
            } else {
                result[bitIndex] = 1;
            }
            bitIndex++;
        }
        return result;
    }

    /**
     * returns the number of bits used to represent bitwise shared numbers and
     * the field size
     * 
     * @return the number of bits used to represent bitwise shared numbers
     */
    public int getBitsCount() {
        return this.bitsCount;
    }

    /**
     * returns the data to send of the running operations for the specified
     * privacy peer
     * 
     * @param privacyPeerIndex
     *            the privacy peer for which to retrieve the data
     * @return the data to send
     */
    public synchronized long[] getDataToSend(final int privacyPeerIndex) {
        // determine the total number of shares to send
        int totalSharesCount = 0;
        for (int i = 0; i < this.operations.size(); i++) {
            if (this.operations.get(i) != null) {
                // increment totalSharesCount by the number of shares for this
                // peer of this operation
                totalSharesCount += this.operations.get(i)
                        .getSharesForPrivacyPeerCount();
            }
        }

        // create array for all the shares to send
        final long[] dataToSend = new long[totalSharesCount];

        // copy shares to send into big array
        int nextSlot = 0;
        for (int i = 0; i < this.operations.size(); i++) {
            if (this.operations.get(i) != null) {
                nextSlot = this.operations.get(i).copySharesForPrivacyPeer(
                        privacyPeerIndex, dataToSend, nextSlot);
            }
        }
        logger.info("getDataToSend returns " + nextSlot + " shares for sending");

        return dataToSend;
    }

    /**
     * @return the fieldSize
     */
    public long getFieldSize() {
        return this.fieldSize;
    }

    /**
     * returns the log of the specified operation
     * 
     * @param operationID
     *            the id of the operation for which to return the log
     * @return the log of the specified operation
     */
    public String getLog(final int operationID) {
        if (this.operationDescriptions != null) {
            String result = "ID=" + operationID + "\nDescription="
                    + getOperationDescription(operationID) + "\nLog:\n";
            result += this.operationLogs.get(operationID);
            return result;
        } else {
            return null;
        }
    }

    /**
     * @return the logger
     */
    public XLogger getLogger() {
        return logger;
    }

    //
    // BEGIN: equal operation specific functions
    //

    /**
     * @return the mpcShamirSharing
     */
    public ShamirSharing getMpcShamirSharing() {
        return this.mpcShamirSharing;
    }

    /**
     * @return the myPrivacyPeerIndex
     */
    public int getMyPrivacyPeerIndex() {
        return this.myPrivacyPeerIndex;
    }

    //
    // END: equal operation specific functions
    //

    /**
     * @return the number of privacy peers
     */
    public int getNumberOfPrivacyPeers() {
        return this.numberOfPrivacyPeers;
    }

    /**
     * returns the description of the specified operation
     * 
     * @param operationID
     *            the id of the operation for which to return the description
     * @return the description of the specified operation
     */
    public String getOperationDescription(final int operationID) {
        if (this.operationDescriptions != null) {
            return this.operationDescriptions.get(operationID);
        } else {
            return null;
        }
    }

    /**
     * returns the specified operation (interface) reference
     * 
     * @param operationID
     *            the id of the operation for which to return the reference
     * @return the specified operation (interface) reference
     */
    public List<IOperation> getOperations() {
        return this.operations;
    }

    /**
     * Returns the predicate Cache.
     * 
     * @return the predicateCache
     */
    public Hashtable<Object, Long> getPredicateCache() {
        return this.predicateCache;
    }

    /**
     * @return the random number generator
     */
    public Random getRandom() {
        return this.random;
    }

    /**
     * returns the final result of the specified operation
     * 
     * @param id
     *            id of the operation for which to retrieve the result
     * @return the final result of the specified operation, or null if the
     *         operation wasn't completed yet
     */
    public long[] getResult(final int id) {
        return this.operationsResults[id];
    }

    /**
     * Initializes the object instance to run all operations of the operation
     * set in parallel. (The operations wont be queued.)
     * 
     * @param operationsCount
     *            the number of operations that shall be run in parallel
     */
    public synchronized void initialize(final int operationsCount) {
        this.parallelOperationsCount = operationsCount;
        this.totalOperationsCount = operationsCount;
        this.operations = new ArrayList<IOperation>(
                this.parallelOperationsCount);
        this.operationsIDs = new ArrayList<Integer>(
                this.parallelOperationsCount);
        this.operationsQueue = null;
        this.operationsResults = new long[this.totalOperationsCount][];
        this.completedOperationsCount = 0;

        /**
         * uncomment the following 2 lines to activate the logging functions for
         * debugging
         */
        // operationDescriptions = new ArrayList<String>(operationsCount);
        // operationLogs = new ArrayList<String>(operationsCount);
    }

    /**
     * Initializes the object instance to run the specified total amount of
     * operations, of which the specified amount will be run in parallel. The
     * additional operations will be queued and automatically started after
     * other operations finished.
     * 
     * @param parallelOperationsCount
     *            the number of operations that shall be run in parallel
     * @param totalOperationsCount
     *            the total number of operations that will be added to this set
     *            of operations
     */
    public synchronized void initialize(final int parallelOperationsCount,
            final int totalOperationsCount) {
        if (parallelOperationsCount >= totalOperationsCount) {
            logger.info("parallelOperationsCount >= totalOperationsCount: "
                    + parallelOperationsCount + " >= " + totalOperationsCount
                    + "; will use initialize(" + totalOperationsCount
                    + ") instead");
            initialize(totalOperationsCount);
            return;
        }
        this.parallelOperationsCount = parallelOperationsCount;
        this.totalOperationsCount = totalOperationsCount;
        this.operations = new ArrayList<IOperation>(parallelOperationsCount);
        this.operationsIDs = new ArrayList<Integer>(parallelOperationsCount);
        this.operationsQueue = new ArrayList<IOperation>(totalOperationsCount
                - parallelOperationsCount);
        this.operationsResults = new long[totalOperationsCount][];
        this.completedOperationsCount = 0;

        /**
         * uncomment the following 2 lines to activate the logging functions for
         * debugging
         */
        // operationDescriptions = new
        // ArrayList<String>(parallelOperationsCount);
        // operationLogs = new ArrayList<String>(parallelOperationsCount);
    }

    /**
     * computes the least significant bit of a shared number
     * <p>
     * If the operation receives the bit shares of a random number as arguments
     * it will use that number for the computation. Otherwise it will try to
     * generate a bitwise shared random number itself and therefore <b>might
     * fail</b> (see {@link #generateBitwiseRandomNumber(int, long[])} ).
     * <p>
     * The final result is a share of the least significant bit (if successful).
     * In case the operation failed the final result is -1.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            the share of the number to compute the LSB of and optionally
     *            the bit shares of the random number that shall be used
     * @return true if the operation was created successfully
     */
    public boolean leastSignificantBit(final int id, final long[] data) {
        // check number of input arguments
        if (data.length != 1 && data.length != 1 + this.bitsCount) {
            logger.warn("creation of least significant bit operation takes either one or "
                    + (1 + this.bitsCount) + " arguments!");
            return false;
        }

        // create and store least significant bit operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new LeastSignificantBit(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new LeastSignificantBit(data));
        }

        return true;
    }

    /**
     * computes if a &lt; b, where a and b can either be shared or public
     * numbers
     * <p>
     * data[0]=a, data[1]=b Depending on the knowledge about a, b, a-b set
     * data[2], data[3], data[4] of the array accordingly:
     * <li>data[2]= 1 if a <= fieldSize/2, 0 if a > fieldSize/2, -1 if nothing
     * is known about a
     * <li>data[3]= 1 if b <= fieldSize/2, 0 if b > fieldSize/2, -1 if nothing
     * is known about b
     * <li>data[4]= 1 if a-b <= fieldSize/2, 0 if a-b > fieldSize/2, -1 if
     * nothing is known about a-b
     * <p>
     * This operation internally uses as many bitwise shared random numbers as
     * there are -1s in data[2], data[3], data[4]. If the operation receives the
     * bit shares of random numbers as arguments (from data[5] on) it will use
     * those numbers for the computation. Otherwise it will generate the bitwise
     * shared random numbers itself and therefore <b>might fail</b> (see
     * {@link #generateBitwiseRandomNumber(int, long[])} ). If the primitives
     * are used via {@link PrimitivesEnabledProtocol#doOperations()}, the total
     * number of required random numbers (for all scheduled lessThans) are
     * calculated and generated at once using
     * {@link BatchGenerateBitwiseRandomNumbers}.
     * <p>
     * The final result is a share of one if a &lt; b and a share of zero
     * otherwise.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            a, b and three values indicating the knowledge about a, b, a-b
     *            and optionally the bit shares of the random numbers that shall
     *            be used
     * @return true if the operation was created successfully
     */
    public boolean lessThan(final int id, final long[] data) {
        // check number of input arguments
        if (data.length != 5 && data.length != 5 + this.bitsCount
                && data.length != 5 + 2 * this.bitsCount
                && data.length != 5 + 3 * this.bitsCount) {
            logger.warn("creation of less-than operation takes either 5, "
                    + (5 + this.bitsCount) + ", " + (5 + 2 * this.bitsCount)
                    + " or " + (5 + 3 * this.bitsCount) + " arguments!");
            return false;
        }

        // create and store less-than operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new LessThan(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new LessThan(data));
        }

        return true;
    }

    /**
     * The same as {@link #lessThan(int, long[])}, but this variant enables
     * predicate caching. See
     * {@link LessThan#setPredicateKeys(Object, Object, Object)}.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            a, b and three values indicating the knowledge about a, b, a-b
     *            and optionally the bit shares of the random numbers that shall
     *            be used
     * @param predicateKeyA
     *            Uniquely identifies secret [A].
     * @param predicateKeyB
     *            Uniquely identifies secret [B].
     * @param predicateKeyAB
     *            Uniquely identifies secret [A-B].
     * @return true if the operation was created successfully
     */
    public boolean lessThan(final int id, final long[] data,
            final Object predicateKeyA, final Object predicateKeyB,
            final Object predicateKeyAB) {
        // check number of input arguments
        if (data.length != 5 && data.length != 5 + this.bitsCount
                && data.length != 5 + 2 * this.bitsCount
                && data.length != 5 + 3 * this.bitsCount) {
            logger.warn("creation of less-than operation takes either 5, "
                    + (5 + this.bitsCount) + ", " + (5 + 2 * this.bitsCount)
                    + " or " + (5 + 3 * this.bitsCount) + " arguments!");
            return false;
        }

        // create and store less-than operation
        final LessThan lt = new LessThan(data);
        lt.setPredicateKeys(predicateKeyA, predicateKeyB, predicateKeyAB);
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(lt);
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount, lt);
        }

        return true;
    }

    /**
     * computes the prefix-or of the value shared with a bit-wise sharing
     * <p>
     * After the operation is completed the final result is an array holding the
     * prefix-or bit shares which contains as many shares as the input data
     * array.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            the bit shares of the value
     * @return true if the operation was created successfully
     */
    public boolean linearPrefixOr(final int id, final long[] data) {
        // check number of input arguments
        if (data == null) {
            logger.warn("creation of linear prefix-or operation requires at least one bit share as input argument!");
            return false;
        } else if (data.length < 1) {
            logger.warn("creation of linear prefix-or operation requires at least one bit share as input argument!");
            return false;
        }

        // create and store linear prefix-or operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new LinearPrefixOr(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new LinearPrefixOr(data));
        }

        return true;
    }

    /**
     * appends the specified entry to the operations log
     * 
     * @param operationID
     *            the id of the operation for which to append the entry to the
     *            log
     * @param entry
     *            the entry to append to the log
     */
    public void log(final int operationID, final String entry) {
        if (this.operationLogs != null) {
            if (this.operationLogs.size() <= operationID) {
                this.operationLogs.add(operationID, entry);
            } else {
                this.operationLogs.set(operationID,
                        this.operationLogs.get(operationID) + "\n" + entry);
            }
        }
    }

    /**
     * appends the specified entry to the current operations log
     * 
     * @param entry
     *            the entry to append to the log
     */
    public void log(final String entry) {
        if (this.operationLogs != null) {
            log(this.currentOperationID, entry);
        }
    }

    /**
     * Computes the minimum of all inputs. data contains the numbers of which
     * the minimum should be found knowledge contains additional knowledge about
     * the numbers:
     * <p>
     * <li>knowledge = 1 if all data[i] <= fieldSize/2, 0 if all data[i] >
     * fieldSize/2, -1 if neither of the above applies.
     * </p>
     * <br>
     * 
     * Only specify 1 or 0 if it holds for all numbers given in data[]. if some
     * numbers are > fieldsize/2 and some are <= fieldsize/2 please specify -1.<br>
     * 
     * The minimum is computed between all even and odd indices is computed in
     * parallel in each round. E.g.:<br>
     * min(data[0],data[1],data[2],data[3],data[4]) -><br>
     * a = min(data[0],data[1]), b = min(data[2],data[3]) c = data[4]<br>
     * d = min(a,b) , c<br>
     * final = min(d, c);<br>
     * <br>
     * 
     * This process is then repeated until there is only 1 result which then is
     * the final result.<br>
     * 
     * There is also the option to compute the minimum in sequenctial manner:
     * E.g.:<br>
     * a = min(data[0], data[1])<br>
     * b = min(a, data[2])<br>
     * c = min(b, data[3])<br>
     * final = min(c, data[4]) ...<br>
     * <br>
     * In general if the minimum of very many values has to be computed it might
     * be better to consider the sequential option (more rounds). The
     * performance might be better than the round optimized variant, since the
     * memory is used more efficiently and the computational load on privacy
     * peers is equal in every round.<br>
     * 
     * You have to try out yourself what works best for your application.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            the shares of the numbers of which the minimum is to be
     *            computed
     * @param knowledge
     *            additional knowledge about the data[i]s
     * @return true if the operation was created successfully
     */
    public boolean min(final int id, final long[] data, final long knowledge,
            final boolean fewRounds) {
        if (data.length < 2) {
            logger.warn("creation of min operation needs at least 2 operands!");
            return false;
        }

        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Min(data, knowledge));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Min(data, knowledge, fewRounds));
        }

        return true;
    }

    /**
     * multiplies two Shamir shares
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing the 2 shares to multiply
     * @return true if function arguments are valid
     */
    public boolean multiply(final int id, final long[] data) {
        // check number of input arguments
        if (data.length != 2) {
            logger.warn("creation of multiplication operation failed: invalid number of arguments!");
            return false;
        }

        // create and store multiplication operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Multiplication(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Multiplication(data));
        }

        return true;
    }

    /**
     * Pops operations from the stack. This overrides currently scheduled
     * operations.
     */
    @SuppressWarnings("unchecked")
    public void popOperations() {
        if (!this.stateVariables.isEmpty()) {
            this.completedOperationsCount = (Integer) this.stateVariables.pop();
            this.totalOperationsCount = (Integer) this.stateVariables.pop();
            this.parallelOperationsCount = (Integer) this.stateVariables.pop();
            this.operationsResults = (long[][]) this.stateVariables.pop();
            /* These three cause "unchecked cast" warnings. */
            this.operationsQueue = (ArrayList<IOperation>) this.stateVariables
                    .pop();
            this.operationsIDs = (ArrayList<Integer>) this.stateVariables.pop();
            this.operations = (ArrayList<IOperation>) this.stateVariables.pop();
        }
    }

    /**
     * computes the n-th power of x, where x is a Shamir shared secret and n a
     * public exponent
     * <p>
     * the exponent has to be at least 1
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing x (first) and n (second)
     * @return true if function arguments are valid
     */
    public boolean power(final int id, final long[] data) {
        // check number of input arguments
        if (data.length != 2) {
            logger.warn("creation of power operation failed: invalid number of arguments!");
            return false;
        }
        // check that exponent is at least 1
        if (!(data[1] >= 1)) {
            logger.warn("creation of power operation failed: exponent must be at least 1!");
            return false;
        }

        // create and store power operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Power(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Power(data));
        }

        return true;
    }

    /**
     * Processes the received data for the running operations (if all data for
     * this step was sent and received yet). Note that if you have multiple
     * threads, each thread needs to call this method and will pick its own part
     * of the work to do.
     * 
     * @throws BrokenBarrierException
     * @throws PrimitivesException
     */
    public void processReceivedData() throws InterruptedException,
            BrokenBarrierException, PrimitivesException {
        if (!areOperationsCompleted()) {
            IOperation state = null;
            final int arrivalNumber = this.barrier.await() + 1;
            if (this.operationsQueue != null) {
                if (this.operations.size() + this.operationsQueue.size() < this.totalOperationsCount) {
                    logger.warn("Not all operations of operation set submitted yet! (Running operations: "
                            + this.operations.size()
                            + "; queued: "
                            + this.operationsQueue.size()
                            + "; expected total: "
                            + this.totalOperationsCount
                            + ")");
                }
            } else {
                if (this.operations.size() < this.totalOperationsCount) {
                    logger.warn("Not all operations of operation set submitted yet! (Running operations: "
                            + this.operations.size()
                            + "; no queue; expected total: "
                            + this.totalOperationsCount + ")");
                }
            }

            // divide operations to process over the number of protocol threads
            final int partitionSize = this.operations.size()
                    / this.protocolThreadsCount;
            int partitionStart = this.operations.size()
                    - (this.protocolThreadsCount - arrivalNumber + 1)
                    * partitionSize;
            if (arrivalNumber == 1) {
                logger.info("processing can begin now: (all data sent and received)");
                partitionStart = 0;
            }
            final int partitionEnd = this.operations.size()
                    - (this.protocolThreadsCount - arrivalNumber)
                    * partitionSize;

            // process the data for all the (non-completed) operations (if not
            // done yet)
            int nextOperationID;
            int myCompletedOperationsCount = 0;
            for (int i = partitionStart; i < partitionEnd; i++) {
                state = this.operations.get(i);
                if (state != null) {
                    /**
                     * uncomment the following line if the logging functions are
                     * used
                     */
                    // currentOperationID = ids[i];
                    state.doStep(this);
                    if (state.isOperationCompleted()) {
                        // store result and get next operation from queue (if
                        // any)
                        this.operationsResults[this.operationsIDs.get(i)] = state
                                .getFinalResult();
                        myCompletedOperationsCount++;
                        nextOperationID = this.operationsIDs.get(i)
                                + this.parallelOperationsCount;
                        // check if the next assigned operation id is valid...
                        if (nextOperationID < this.totalOperationsCount) {
                            // ... if so, dequeue, create and execute first step
                            // of next operation
                            state = this.operationsQueue.get(nextOperationID
                                    - this.parallelOperationsCount);
                            this.operationsQueue.set(nextOperationID
                                    - this.parallelOperationsCount, null);
                            state.doStep(this);
                            this.operationsIDs.set(i, nextOperationID);
                            this.operations.set(i, state);
                        } else {
                            this.operationsIDs.set(i, -1);
                            this.operations.set(i, null);
                        }
                    }
                }
            }
            synchronized (this) {
                this.completedOperationsCount += myCompletedOperationsCount;
            }
            this.barrier.await();
        } else {
            logger.info("processReceivedData: operations already completed");
        }
    }

    /**
     * computes the product of several factors
     * <p>
     * The final result is a share of the product.
     * <p>
     * The amount of rounds required is logarithmic in the number of factors.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing the shares of the factors
     * @return true if operation was created successfully
     */
    public boolean product(final int id, final long[] data) {
        // check input arguments
        if (data == null) {
            logger.warn("creation of product operation failed: no arguments!");
            return false;
        }
        if (data.length < 2) {
            logger.warn("creation of product operation failed: insufficient number of arguments!");
            return false;
        }

        // create and store product operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Product(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Product(data));
        }

        return true;
    }

    /**
     * Push the current operations on a stack for backup.
     */
    public void pushOperations() {
        this.stateVariables.push(this.operations);
        this.stateVariables.push(this.operationsIDs);
        this.stateVariables.push(this.operationsQueue);
        this.stateVariables.push(this.operationsResults);
        this.stateVariables.push(this.parallelOperationsCount);
        this.stateVariables.push(this.totalOperationsCount);
        this.stateVariables.push(this.completedOperationsCount);
    }

    /**
     * reconstructs a shared secret.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing the share to reconstruct
     * @return true if function arguments are valid
     */
    public boolean reconstruct(final int id, final long[] data) {
        // check number of input arguments
        if (data.length != 1) {
            logger.warn("creation of reconstruction operation failed: invalid number of arguments!");
            return false;
        }

        // create and store reconstruction operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Reconstruction(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Reconstruction(data));
        }

        return true;
    }

    /**
     * sets the description of the specified operation
     * 
     * @param operationID
     *            the id of the operation for which to specify the description
     * @param description
     *            the operations description to set
     */
    public void setOperationDescription(final int operationID,
            final String description) {
        if (this.operationDescriptions != null) {
            this.operationDescriptions.add(operationID, description);
        }
    }

    /**
     * Sets the optimal field size for the specified amount of
     * {@link #equal(int, long[])} and
     * {@link #generateBitwiseRandomNumber(int, long[])} operations, s.t. the
     * given maxValue fits into the field and the overall running time is
     * minimized.
     * 
     * @param maxValue
     *            the largest value that has to fit into the field
     * @param equalCount
     *            the number of equal operations executed by the protocol
     * @param bitwiseRandomNumbersCount
     *            the number of bitwise (shared) random number generation
     *            operations executed by the protocol
     * @return the selected field size
     * @throws Exception
     */
    public long setOptimalFieldSize(final long maxValue, final long equalCount,
            final long bitwiseRandomNumbersCount) throws Exception {
        // look in array for smallest prime which is larger than the input value
        final long[] primes = FieldSizeCandidate.getPrimes();
        float minEffort = Float.MAX_VALUE;
        int best = -1;
        float effort = Float.MAX_VALUE;
        for (int i = 0; i < primes.length; i++) {
            if (primes[i] > maxValue) {
                effort = FieldSizeCandidate.computeEqualEffort(primes[i])
                        * equalCount
                        + FieldSizeCandidate
                                .computeBitwiseRandomNumberGenerationEffort(i,
                                        bitwiseRandomNumbersCount, 0.9F);
                if (effort < minEffort) {
                    minEffort = effort;
                    best = i;
                }
            }
        }

        if (best < 0) {
            // default prime: biggest prime smaller than MAX_LONG
            this.fieldSize = 9223372036854775783L;
        } else {
            this.fieldSize = primes[best];
        }

        this.bitsCount = Long.toBinaryString(this.fieldSize).length();
        this.mpcShamirSharing.setFieldSize(this.fieldSize);
        this.mpcShamirSharing.init();
        return this.fieldSize;
    }

    /**
     * Sets the optimal field size for the {@link #equal(int, long[])}
     * operation, s.t. the given maxValue fits into the field and the operation
     * runs as fast as possible.
     * 
     * @param maxValue
     *            the largest value that has to fit into the field
     * @throws Exception
     */
    public void setOptimalFieldSizeForEqual(final long maxValue)
            throws Exception {
        this.fieldSize = Equal.getOptimalFieldSizeForEqual(maxValue);
        this.bitsCount = Long.toBinaryString(this.fieldSize).length();
        this.mpcShamirSharing.setFieldSize(this.fieldSize);
        this.mpcShamirSharing.init();
    }

    /**
     * sets the data received from the privacy peer for the running operations
     * 
     * @param privacyPeerIndex
     *            the index of the peer for which to set the data
     * @param data
     *            the data for the operations
     */
    public synchronized void setReceivedData(final int privacyPeerIndex,
            final long[] data) {
        int nextSlot = 0;
        int runningOperationsCount = 0;
        for (int idIndex = 0; idIndex < this.operations.size(); idIndex++) {
            if (this.operations.get(idIndex) != null) {
                nextSlot = this.operations.get(idIndex)
                        .copySharesFromPrivacyPeer(privacyPeerIndex, data,
                                nextSlot);
                runningOperationsCount++;
            }
        }
        if (data != null) {
            logger.info("setReceivedData set " + data.length + " shares for "
                    + runningOperationsCount + " operations");
        } else {
            logger.info("setReceivedData set: DUMMY shares for "
                    + runningOperationsCount + " operations");
        }
    }

    /**
     * tests if a Shamir share (x) is in the specified publicly known interval:
     * x \in [l,u]
     * <p>
     * The final result is a share of one if the share is in the interval and a
     * share of zero otherwise.
     * <p>
     * This interval test function is only suitable for small intervals. The
     * amount of multiplications used internally is linear in the interval size
     * (and logarithmic in the field size). The amount of rounds required is
     * logarithmic in the interval (and field) size.
     * <p>
     * Note: This operation only works if the fieldSize is prime.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing the share, the public lower bound and the
     *            upper bound: [x, l, u]
     * @return true if operation was created successfully
     */
    public boolean smallIntervalTest(final int id, final long[] data) {
        // check input arguments
        if (data.length != 3) {
            logger.warn("creation of small interval test operation failed: invalid number of arguments!");
            return false;
        }
        if (!(0 <= data[1] && data[1] <= data[2] && data[2] < this.fieldSize)) {
            logger.warn("creation of small interval test operation failed: bounds must be valid field elements and upper bound must be at least as large as lower bound!");
            return false;
        }

        // create and store small interval test operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new SmallIntervalTest(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new SmallIntervalTest(data));
        }

        return true;
    }

    /**
     * Synchronizes an array of {0,1} values.
     * 
     * @param id
     *            id of the operation
     * @param data
     *            array containing the bit values
     * @return the logical AND of the bits at each position
     */
    public boolean synchronize(final int id, final long[] data) {
        // create and store multiplication operation
        if (this.operations.size() < this.parallelOperationsCount) {
            this.operations.add(new Synchronization(data));
            this.operationsIDs.add(id);
        } else {
            this.operationsQueue.add(id - this.parallelOperationsCount,
                    new Synchronization(data));
        }

        return true;
    }

}
