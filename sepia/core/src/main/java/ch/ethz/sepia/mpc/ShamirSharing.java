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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

import ch.ethz.sepia.mpc.protocolPrimitives.PrimitivesException;
import ch.ethz.sepia.services.Utils;

/**
 * Secret Sharing using Shamir's Secret Sharing Scheme (SSS).
 * <p>
 * All computations are done within the finite field, i.e., in [0, fieldSize-1]
 *
 * @author Lisa Barisic, Dilip Many, ETH Zurich
 */
public class ShamirSharing {

	/**
	 * This is the default field size.
	 * Use this field size if you want to ensure that all operations on shares can be
	 * done using the long data type only and don't require BigInteger operations.
	 */
	public static final long FIELD_SIZE_PRIME_31BITS = 1401085391L;
    private static final long DEFAULT_FIELD_SIZE = FIELD_SIZE_PRIME_31BITS;

    public static final long FIELD_SIZE_PRIME_62BITS = 3775874107000403461L;
	/**
	 * This is the biggest prime smaller than MAX_LONG.
	 */
	public static final long FIELD_SIZE_PRIME_63BITS = 9223372036854775783L;
	private static final XLogger logger = new XLogger(LoggerFactory.getLogger(ShamirSharing.class));
	/**
	 * Use this value to represent shares that are missing, e.g., due to a privacy peer that failed to
	 * deliver its shares.
	 */
	public static final long MISSING_SHARE = -1;
	private int[] alphas = null;
	/** BigInteger representation of the field size */
	private BigInteger bigFieldSize = null;
	private BigInteger bigIntFieldSize = null;
	private int degreeT = -1;
	/** the size of the finite field */
	private long fieldSize = 0;
	/** the number of peers among which the secret is shared */
	private int numberOfPrivacyPeers = 0;
	/** For a given set of available privacy peers, this map holds precomputed Lagrange weights */
	private HashMap<String, long[]> precomputedLagrangeWeights;
	private Random random = null;

	//private SecureRandom random = null;
	private String randomAlgorithm = null;
	private long[][] sharingMatrix = null;

	/** indicates if addition has to be done internally using BigIntegers to avoid overflow */
	private boolean useBigIntegerAddition = false;
	/** indicates if multiplication has to be done internally using BigIntegers to avoid overflow */
	private boolean useBigIntegerMultiplication = false;


	/**
	 * creates a MpcShamirSharing instance with the default group order
	 */
	public ShamirSharing() {
		setFieldSize(DEFAULT_FIELD_SIZE);
	}


	/**
	 * creates a MpcShamirSharing instance with the specified group order
	 * to share secrets among the specified number of peers
	 *
	 * @param numberOfPeers		the number of peers among which the secret is shared
	 * @throws Exception
	 */
	public ShamirSharing(final int numberOfPeers) throws Exception {
		this();
		this.numberOfPrivacyPeers = numberOfPeers;

		computeAlphas();
	}


	/**
	 * creates a MpcShamirSharing instance with the specified group order
	 * to share secrets among the specified number of peers
	 *
	 * @param numberOfPeers		the number of peers among which the secret is shared
	 * @param randomAlgorithm	[currently unused]
	 * @throws Exception
	 */
	public ShamirSharing(final int numberOfPeers, final String randomAlgorithm) throws Exception {
		this(numberOfPeers);
		this.randomAlgorithm = randomAlgorithm;
	}

	/**
	 *  Set alpha points for sharing polynomials
	 */
	private synchronized void computeAlphas() {
		logger.info("Alphas:");
		alphas = new int[numberOfPrivacyPeers];
		String line = "";
		for (int i = 0; i < numberOfPrivacyPeers; i++) {
			// don't want an alpha = 1, since 1^q = 1...
			alphas[i] = i + 2;  // or some other value (except 0), but all participants must have the same !(?)
			line = line + " " + alphas[i];
		}
		logger.info(line);
	}

	/**
	 * Computes the Lagrange weights used for interpolation to get secrets.
	 *
	 * @param availablePrivacyPeers Indicates which privacy peers are available for reconstruction.
	 * If the set of privacy peers changes, the weights have to be recomputed.
	 * @return the Lagrange weights
	 */
	private long[] computeLagrangeWeights(final boolean[] availablePrivacyPeers) {
		final long[] lagrangeWeights = new long[numberOfPrivacyPeers];

		for (int privacyPeer = 0; privacyPeer < numberOfPrivacyPeers; privacyPeer++) {
			if(availablePrivacyPeers[privacyPeer]) {
				long weight = 1;
				long nominator = 1;
				long denominator = 1;
				final int alphaP = alphas[privacyPeer];

				for (int ppIndex = 0; ppIndex < alphas.length; ppIndex++) {
					if (ppIndex != privacyPeer && availablePrivacyPeers[ppIndex]) {
						nominator = modMultiply(nominator, alphas[ppIndex]);
						denominator = modMultiply(denominator, modSubtract((long)alphas[ppIndex], (long)alphaP));
					}
				}

				weight = modMultiply(nominator, inverse(denominator));
				lagrangeWeights[privacyPeer]=weight;
			} else {
				lagrangeWeights[privacyPeer] = 0;
			}
		}
		return lagrangeWeights;
	}

	/**
	 * Generate the sharing matrix (to compute function at points alpha_i).
	 * <p>
	 * For the first sharing only <degreeT+1> entries are needed, but for later
	 * use (sharing of the multiplied shares) we'll need the (2t+1)x(2t+1) matrix.
	 * So this is just a precomputation of all values needed later.
	 */
	protected void computeSharingMatrix() {
		sharingMatrix = new long[numberOfPrivacyPeers][numberOfPrivacyPeers];

		for (int i = 0; i <
		numberOfPrivacyPeers; i++) {
			for (int j = 0; j < numberOfPrivacyPeers; j++) {
				sharingMatrix[i][j] = fastExponentiation(alphas[i], j);
			}
		}
	}

	/**
	 * Fast exponentiation using {@link BigInteger#modPow(BigInteger, BigInteger)}.
	 *
	 * @param term the term to be exponentiated
	 * @param exponent the exponent
	 * @return (term^exponent) mod fieldSize
	 */
	public long fastExponentiation(final long term, final long exponent) {
		final BigInteger t = BigInteger.valueOf(term);
		final BigInteger e = BigInteger.valueOf(exponent);
		return t.modPow(e, bigIntFieldSize).longValue();
	}

	/**
	 * Given the secret generate random coefficients (except for a_0 which is
	 * the secret) and compute the function for each privacy peer (who is
	 * assigned a dedicated alpha). Coefficients are picked from [0, fieldSize).
	 *
	 * @param secret	the secret to be shared
	 * @return			the shares of the secret for each privacy peer
	 */
	public long[] generateShare(final long secret) {
		long nextCoefficient;
		final long[] shares = new long[alphas.length];

		// At first compute the coefficients
		for(int degree = 0; degree < degreeT + 1; degree++) {
			if(degree == 0) {
				// Coefficient a_0 is the secret
				nextCoefficient = secret;
			} else {
				// Coefficients for Shamir shares are picked from [0, fieldSize).
				nextCoefficient = mod(random.nextLong());
			}
			for(int privacyPeerIndex = 0; privacyPeerIndex < alphas.length; privacyPeerIndex++) {
				shares[privacyPeerIndex] = modAdd(shares[privacyPeerIndex], modMultiply(sharingMatrix[privacyPeerIndex][degree], nextCoefficient));
			}
		}

		return shares;
	}

	/**
	 * Given the secrets generate random coefficients (except for a_0 which is
	 * the secret) and compute the function for each peer (who is assigned a
	 * dedicated alpha). Coefficients are picked from [0, fieldSize).
	 *
	 * @param secrets	The list of the secrets to be shared
	 *
	 * @return			The shares of each peer for the secrets ([privacyPeer][secretNr])
	 */
	public long[][] generateShares(final long[] secrets) {
		long nextCoefficient;
		final long[][] shares = new long[alphas.length][secrets.length];

		// At first compute the coefficients for each secret
		for (int secret = 0; secret < secrets.length; secret++) {
			for (int degree = 0; degree < degreeT + 1; degree++) {
				if (degree == 0) {
					// Coefficient a_0 is the secret
					nextCoefficient = secrets[secret];
				} else {
					// Coefficients for Shamir shares are picked from [0, fieldSize).
					nextCoefficient = mod(random.nextLong());
				}
				for (int peer = 0; peer < alphas.length; peer++) {
					shares[peer][secret] = modAdd(shares[peer][secret], modMultiply(sharingMatrix[peer][degree], nextCoefficient));
				}
			}
		}

		return shares;
	}


	/**
	 * Gets the degree t of the polynomials used for secret sharing.
	 * @return the degreeT
	 */
	public int getDegreeT() {
		return degreeT;
	}


	/**
	 * returns the field size of the finite field which is used for the Shamir Secret Sharing.
	 *
	 * @return the size of the finite field
	 */
	public long getFieldSize() {
		return fieldSize;
	}


	/**
	 * Returns the Lagrange weights for the set of available privacy peers.
	 * @param responsivePrivacyPeers
	 * @return the Lagrange weights.
	 */
	private long[] getLagrangeWeights(final boolean[] availablePrivacyPeers) {
		// Using the boolean[] as a key does not work because it does not implement value equality
		final String key = Arrays.toString(availablePrivacyPeers);
		long[] lagrangeWeights = precomputedLagrangeWeights.get(key);
		if (lagrangeWeights==null) {
			lagrangeWeights = computeLagrangeWeights(availablePrivacyPeers);
			precomputedLagrangeWeights.put(key, lagrangeWeights);
		}
		return lagrangeWeights;
	}


	/**
	 * returns the number of peers among which the secret is shared
	 *
	 * @return the number of peers.
	 */
	public int getNumberOfPrivacyPeers() {
		return numberOfPrivacyPeers;
	}

	public String getRandomAlgorithm() {
		return randomAlgorithm;
	}


	/**
	 * initializes the MpcShamirSharing instance
	 */
	public void init() {
		final BigInteger fs = BigInteger.valueOf(fieldSize);
		if (!fs.isProbablePrime(10)) {
			logger.warn("The field size "+fieldSize+" is not prime! Thus, interpolation won't work!");
		}

		// do overflowTest at the very beginning before using any (modular) computation functions
		overflowTest();

		if (degreeT == -1) {
			// if the degree was not initialized, set it to the maximum.
			degreeT = (numberOfPrivacyPeers - 1) / 2;
		} else {
			if (degreeT > (numberOfPrivacyPeers - 1) / 2) {
				logger.warn("Degree of polynomials is too big for multiplications. " +
						"For multiplications to work, m>=2t+1 must hold. (m="+numberOfPrivacyPeers+", t="+degreeT+")");
			}
		}

		logger.info(degreeT + 1 + " out of " + numberOfPrivacyPeers + " privacy peers must be available to reconstruct secrets. (m="
				+ numberOfPrivacyPeers + ", t=" + degreeT + ")");

		computeAlphas();
		computeSharingMatrix();
		logSharingMatrix();

		precomputedLagrangeWeights = new HashMap<String, long[]>();
	}


	/**
	 * Interpolates the result from the given shares. Throws an exception if not
	 * enough shares are available.
	 *
	 * @param shares
	 *            the shares from which to interpolate the result. Set a share
	 *            to {@link #MISSING_SHARE} to indicate a "missing" privacy
	 *            peer.
	 * @param isMultiplication
	 *            indicates whether this interpolation is part of a
	 *            multiplication operation with an intermediate polynomial of
	 *            degree 2t.
	 * @return Result when interpolating given share points
	 * @throws PrimitivesException
	 */
	public long interpolate(final long[] shares, final boolean isMultiplication) throws PrimitivesException {
		long interpolationResult = 0;

		/*
		 * Count the number of available shares and make check if enough
		 * shares are available for interpolation.
		 */
		final boolean[] availableShares = new boolean[numberOfPrivacyPeers];
		int numberOfAvailableShares=0;
		for(int i=0; i<numberOfPrivacyPeers; i++) {
			if(shares[i]!=MISSING_SHARE) {
				availableShares[i]=true;
				numberOfAvailableShares++;
			}
		}

		if (numberOfAvailableShares <= degreeT) {
			throw new PrimitivesException(
					"Not enough shares for interpolation! Need at least t+1. (m="
							+ numberOfPrivacyPeers + ", t=" + degreeT
							+ ", #shares=" + numberOfAvailableShares + ")");
		}

		if (isMultiplication && numberOfAvailableShares < 2 * degreeT + 1) {
			throw new PrimitivesException(
					"Not enough shares to perform private multiplication! "
							+ "For multiplications to work, m>=2t+1 must hold. (m="
							+ numberOfAvailableShares + ", t=" + degreeT + ")");
		}

		final long[] lagrangeWeights = getLagrangeWeights(availableShares);

		// Now interpolate
		for (int privacyPeer = 0; privacyPeer < shares.length; privacyPeer++) {
			if (availableShares[privacyPeer]) {
				interpolationResult = modAdd(interpolationResult, modMultiply(lagrangeWeights[privacyPeer], shares[privacyPeer]));
			}
		}
		return interpolationResult;
	}


	/**
	 * Computes the multiplicative inverse of the given value
	 * (in the finite field used for the sharing) using {@link BigInteger#modInverse(BigInteger)}.
	 *
	 * @param value		the value of which to compute the multiplicative inverse
	 * @return			the multiplicative inverse
	 */
	public long inverse(final long value) {
		final BigInteger v = BigInteger.valueOf(value);
		return v.modInverse(bigIntFieldSize).longValue();
	}


	/**
	 * computes the Legendre symbol
	 * <p>
	 * This function doesn't return "-1" instead it will return the equivalent
	 * of the field: (fieldSize-1)
	 * Therefore this function either returns 0, 1 or (fieldSize-1).
	 *
	 * @param a		the value to compute the Legendre symbol of
	 * @return		the Legendre symbol
	 */
	private long legendreSymbol(final long a) {
		long result = 0;
		try{
			result = fastExponentiation(a, (fieldSize-1)/2);
		}
		catch (final Exception e) {
			logger.error("fast exponentiation failed: " + Utils.getStackTrace(e));
		}
		return result;
	}


	/**
	 * Logs the sharing matrix.
	 */
	protected void logSharingMatrix() {
		final StringBuilder line = new StringBuilder("Sharing matrix: [");
		for (int i = 0; i < numberOfPrivacyPeers; i++) {
		    if (i > 0) {
		        line.append(',');
		    }
			for (int j = 0; j < numberOfPrivacyPeers; j++) {
			    if (j > 0 || i > 0) {
			        line.append(' ');
			    }
				line.append(sharingMatrix[i][j]);
			}
		}
		line.append(']');
		logger.info(line.toString());
	}


	/**
	 * Unfortunately, the java operator "%" is not exactly a modulo operation but
	 * a remainder operation. That is, a%b can be negative if a is negative.
	 * To stay within GF(p), however, we need a real modulo operation.
	 * @param a
	 * @return a modulo the field size
	 */
	public long mod(long a) {
		a = a % fieldSize;
		return a >= 0 ? a : a + fieldSize;
	}


	/**
	 * adds the two values a, b together, modulo the size of the field
	 *
	 * @param a		one of the values to add together
	 * @param b		the other value to add
	 * @return		the sum of the two values modulo the field size
	 */
	public long modAdd(final long a, final long b) {
		if(!useBigIntegerAddition) {
			return (a+b) % fieldSize;
		} else {
			return BigInteger.valueOf(a).add(BigInteger.valueOf(b)).mod(bigFieldSize).longValue();
		}
	}

	/**
	 * multiplies the two values a, b modulo the group order of the field
	 *
	 * @param a
	 * @param b
	 * @return		the product of the two values modulo the field size
	 */
	public long modMultiply(final long a, final long b) {
		if(!useBigIntegerMultiplication) {
			return a*b % fieldSize;
		} else {
			return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).mod(bigFieldSize).longValue();
		}
	}


	/**
	 * Computes the modular square root of a, e.g. computes x, s.t.
	 * x^2 = a mod fieldSize. The implementation is based on the
	 * <a href="http://en.wikipedia.org/wiki/Shanks-Tonelli_algorithm">
	 * Shanks-Tonelli algorithm
	 * </a>.
	 *
	 * NOTE: This method doesn't work for field sizes which are composite numbers.
	 *
	 * @param a		the value of which to compute the modular square root
	 * @return		the modular square root of a or 0 if a has none (or it
	 *				couldn't be computed)
	 */
	public long modSqrt(final long a) {
		// check if input value has a modular square root
		if(legendreSymbol(a) != 1L) {
			// a has no modular square root
			logger.info("input value has no modular square root");
			return 0;
		} else if( fieldSize % 4 == 3) {
			try {
				return fastExponentiation(a, (fieldSize+1)/4);
			}
			catch (final Exception ex) {
				logger.error("fast exponentiation failed: " + Utils.getStackTrace(ex));
			}
		}

		// find max. power of 2 in fieldSize-1
		long s = fieldSize-1;
		int e = 0;
		for(e = 0; e < 64; e++) {
			if( s % 2==1 ) {
				break;
			}
			s /= 2;
		}

		// find a quadratic non-residue
		long nonResidue = 0;
		for(long i = 2; i < fieldSize; i++) {
			if(legendreSymbol(i) == fieldSize-1) {
				nonResidue = i;
				break;
			}
		}
		if(nonResidue == 0) {
			logger.warn("failed finding quadratic non-residue");
			return 0;
		}

		try {
			// guess root and fudge factor
			long rootGuess = fastExponentiation(a, (s+1)/2);
			long fudgeFactorGuess = fastExponentiation(a, s);
			long g = fastExponentiation(nonResidue, s);
			long r = e;
			long m = 0;

			// improve root and fudge factor guesses until we found the correct root
			long temp = 0;
			while(true) {
				temp = fudgeFactorGuess;
				for(m = 0; m < r; m++) {
					if(temp == 1L) {
						break;
					}
					temp = modMultiply(temp, temp);
				}
				if(m==0) {
					return rootGuess;
				} else {
					temp = fastExponentiation(g, 1L << r-m-1 );
					rootGuess = modMultiply(rootGuess, temp);
					g = modMultiply(temp, temp);
					fudgeFactorGuess = modMultiply(fudgeFactorGuess, g);
					r = m;
				}
			}
		}
		catch (final Exception ex) {
			logger.error("fast exponentiation failed: " + Utils.getStackTrace(ex));
		}

		return 0;
	}


	/**
	 * computes a-b modulo the size of the field
	 *
	 * @param a		the value to subtract from
	 * @param b		the value to subtract
	 * @return		the difference modulo the field size
	 */
	public long modSubtract(final long a, final long b) {
		if(!useBigIntegerAddition) {
			return mod(a-b);
		} else {
			return BigInteger.valueOf(a).subtract(BigInteger.valueOf(b)).mod(bigFieldSize).longValue();
		}
	}


	/**
	 * tests how addition and multiplication have to be done s.t. they don't overflow
	 * (e.g: multiplication of 2 field elements might overflow the long range although
	 * a field element itself fits into a long)
	 */
	private void overflowTest() {
		// recompute BigInteger representation of the field size
		bigFieldSize = BigInteger.valueOf(fieldSize);

		final BigInteger longMax = BigInteger.valueOf(Long.MAX_VALUE);
		final BigInteger fieldElementMax = BigInteger.valueOf(fieldSize-1);

		// check if BigInteger operations have to be used to avoid overflow
		if( fieldElementMax.add(fieldElementMax).compareTo(longMax) > 0 ) {
			useBigIntegerAddition = true;
		} else {
			useBigIntegerAddition = false;
		}
		if( fieldElementMax.multiply(fieldElementMax).compareTo(longMax) > 0 ) {
			useBigIntegerMultiplication = true;
		} else {
			useBigIntegerMultiplication = false;
		}
	}


	/**
	 * prints the title and the given shares to the log
	 *
	 * @param shares	the 2-dimensional shares array to print
	 * @param title		the title to print
	 */
	protected void printShares(final long[][] shares, final String title) {
		String line;

		logger.info(title);

		for (int timeSlot = 0; timeSlot < shares.length; timeSlot++) {
			line = "Timeslot # " + timeSlot + ":\t";
			for (int i = 0; i < shares[0].length; i++) {
				line += shares[timeSlot][i] + "\t";
			}
			line += "\t//\t";
			logger.info(line);
		}
	}

	/**
	 * prints the title and the given shares to the log
	 *
	 * @param shares	the 3-dimensional shares array to print
	 * @param title		the title to print
	 */
	protected void printShares(final long[][][] shares, final String title) {
		String line;

		logger.info(title);
		for (int timeSlot = 0; timeSlot < shares.length; timeSlot++) {
			line = "Timeslot # " + timeSlot + ":\t";
			for (int secret = 0; secret < shares[0].length; secret++) {
				line += "secret # " + secret + ": \t";
				for (int i = 0; i < shares[0][0].length; i++) {
					line += shares[timeSlot][secret][i] + "\t";
				}
				line += "\t//\t";
			}
			logger.info(line);
		}
	}


	/**
	 * Sets the degree t of the polynomials used for secret sharing. If -1 is provided,
	 * the default of t=(m-1)/2 is selected.
	 * @param degreeT the degreeT to set
	 */
	public void setDegreeT(final int degreeT) {
		this.degreeT = degreeT;
	}

	/**
	 * Sets the size of the finite field which is used for the Shamir Secret Sharing.
	 * The field size must be chosen s.t. all elements in [0,fieldSize-1] have
	 * a multiplicative inverse. (e.g: choose fieldSize to be prime)
	 *
	 * @param fieldSize
	 */
	public void setFieldSize(final long fieldSize) {
		this.fieldSize = fieldSize;
		bigIntFieldSize = BigInteger.valueOf(fieldSize);
	}


	/**
	 * Sets the number of participating peers and precomputes the new
	 * Lagrange weights for reconstruction
	 */
	public void setNumberOfPrivacyPeers(final int numberOfPrivacyPeers) {
		this.numberOfPrivacyPeers = numberOfPrivacyPeers;
	}


	public void setRandomAlgorithm(final String randomAlgorithm) {
		this.randomAlgorithm = randomAlgorithm;
		//this.random = SecureRandom.getInstance(randomAlgorithm);
		this.random = new Random();
	}


    /**
     * adds two vectors.
     *
     * @param v1		first vector
     * @param v2		second vector
     * @return			the resulting vector
     *
     * @throws			IllegalArgumentException if the lengths of the vectors do not match.
     */
    public long[] vectorAdd(final long[] v1, final long[] v2) {
        if(v1.length!= v2.length) {
        	throw new IllegalArgumentException("vector lengths do not match!");
        }
        final long[] result = new long[v1.length];
        for(int j = 0; j < result.length; j++) {
        	result[j] = modAdd(v1[j], v2[j]);
        }
        return result;
    }
}
