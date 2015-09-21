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

package ch.ethz.sepia;

import java.math.BigInteger;
import java.util.Random;

import junit.framework.TestCase;
import ch.ethz.sepia.mpc.ShamirSharing;
import ch.ethz.sepia.services.Utils;

/**
 * Class to test the MpcShamirSharing class
 *
 * @author Dilip Many
 *
 */
public class ShamirSharingTest extends TestCase {

	/** the number of input values from the lower and upper end of the input value range to use in the input */
	private int borderSize = 10;
	/** the number of random values to use in the input */
	private int randomValuesCount = 100;
	/** the number of privacy peers */
	private int numberOfPrivacyPeers = 7;
	/** name of random algorithm (used for Shamir sharing) */
	private String randomAlgorithm = "SHA1PRNG";
	/** the group orders used to test with */
	long[] groupOrders = {17, 257, 1153, 4129, 12289, 18433, 32833, 40961, 65537,
							524353, 8388617, 8650753, 9999991, 1401085391, 2147352577,
							1111111111111111111L, 2305843009213693951L, 3775874107000403461L, 9223372036854775783L};

	private ShamirSharing mpcShamirSharing = null;
	/** the group order of the finite field used in the Shamir sharing */
	private long maxCoefficient = 12289L;
	/** random number generator */
	private Random random = null;
	/** the input values */
	private long[] input = null;



	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}


	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}


	/**
	 * Test method for {@link mpc.ShamirSharing#interpolate(long[])}.
	 * The degree t of polynomials and the number of "missing" privacy peers is varied.
	 */
	public void testInterpolation() {
		Random rand = new Random(System.currentTimeMillis());
		for(int groupOrderIndex = 0; groupOrderIndex < groupOrders.length; groupOrderIndex++) {
			System.out.println("\ntesting with exponent=" + maxCoefficient);
			for(int degreeT=1; degreeT<numberOfPrivacyPeers; degreeT++) {
				try{
					maxCoefficient = groupOrders[groupOrderIndex];
					mpcShamirSharing = new ShamirSharing();
					mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
					mpcShamirSharing.setFieldSize(maxCoefficient);
					mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
					mpcShamirSharing.setDegreeT(degreeT);
					mpcShamirSharing.init();
				}
				catch (Exception e) {
					fail("An exception occured when creating and initializing a MpcShamirSharing instance: " +Utils.getStackTrace(e));
				}
				createInputValues();
				long[][] computedResult = mpcShamirSharing.generateShares(input);
	
				long[] shares = new long[computedResult.length];
				long reconstructed = 0;
				for(int secretIndex = 0; secretIndex < input.length; secretIndex++) {
					for(int peerIndex = 0; peerIndex < numberOfPrivacyPeers; peerIndex++) {
						shares[peerIndex] = computedResult[peerIndex][secretIndex];
					}
					
					int availablePPs = numberOfPrivacyPeers;
					while(availablePPs > degreeT) {
						try{
							//System.out.println("Interpolating with "+availablePPs+"/"+numberOfPrivacyPeers+" PPs. (t="+degreeT+")");
							reconstructed = mpcShamirSharing.interpolate(shares, false);
						}
						catch (Exception e) {
							fail("An exception occured when interpolating: " +Utils.getStackTrace(e));
						}
						assertEquals("reconstructing "+input[secretIndex]+": ", input[secretIndex], reconstructed);
					
						// Now remove a random share and check whether the secret can still be reconstructed in the next round.
						int failingPP = rand.nextInt(numberOfPrivacyPeers);
						boolean erasedShare=false;
						while(!erasedShare) {
							if (shares[failingPP] >=0 ) {
								shares[failingPP] = -1;
								erasedShare = true;
							} else {
								failingPP = rand.nextInt(numberOfPrivacyPeers);
							}
						}
						availablePPs--;
					}
				}
				System.out.println("Degree="+degreeT+". Interpolation of "+input.length+" secrets worked with "+(degreeT+1)+" to "+numberOfPrivacyPeers+" available PPs.");
			}
		}
	}


	/**
	 * Test method for {@link mpc.ShamirSharing#generateShares(long[])}.
	 */
	public void testGenerateShares() {
		for(int groupOrderIndex = 0; groupOrderIndex < groupOrders.length; groupOrderIndex++) {
			try{
				maxCoefficient = groupOrders[groupOrderIndex];
				mpcShamirSharing = new ShamirSharing();
				mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
				mpcShamirSharing.setFieldSize(maxCoefficient);
				mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
				mpcShamirSharing.init();
			}
			catch (Exception e) {
				fail("An exception occured when creating and initializing a MpcShamirSharing instance: " +Utils.getStackTrace(e));
			}
			createInputValues();

			System.out.println("\ntesting with exponent=" + maxCoefficient);
			long[][] computedResult = mpcShamirSharing.generateShares(input);
			// check that all shares are valid group elements
			for(int i = 0; i < computedResult.length; i++) {
				for(int j = 0; j < computedResult[i].length; j++) {
					assertTrue("test that share ("+computedResult[i][j]+") is positive: ", computedResult[i][j] >= 0);
					assertTrue("test that share ("+computedResult[i][j]+") is smaller than group order("+ maxCoefficient +"): ", computedResult[i][j] < maxCoefficient);
				}
			}
		}
	}


	/**
	 * Test method for {@link mpc.ShamirSharing#inverse(long)}.
	 */
	public void testInverse() {
		long computedResult = 0;
		BigInteger bigComputedResult = null;
		BigInteger one = new BigInteger("1");
		BigInteger bigGroupOrder = null;
		BigInteger bigProduct = null;

		for(int groupOrderIndex = 0; groupOrderIndex < groupOrders.length; groupOrderIndex++) {
			try{
				maxCoefficient = groupOrders[groupOrderIndex];
				mpcShamirSharing = new ShamirSharing();
				mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
				mpcShamirSharing.setFieldSize(maxCoefficient);
				mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
				mpcShamirSharing.init();
			}
			catch (Exception e) {
				fail("An exception occured when creating and initializing a MpcShamirSharing instance: " +Utils.getStackTrace(e));
			}
			createInputValues();

			System.out.println("\ntesting with exponent=" + maxCoefficient);
			bigGroupOrder = BigInteger.valueOf(maxCoefficient);
			for(int i = 1; i < input.length; i++) {
				// check that input value is a valid group element and >0
				if((input[i] < maxCoefficient) && (input[i] > 0)) {
					computedResult = mpcShamirSharing.inverse(input[i]);
					bigComputedResult = BigInteger.valueOf(computedResult);
					bigProduct = bigComputedResult.multiply(BigInteger.valueOf(input[i])).mod(bigGroupOrder);
					assertEquals("testing mathematical inverse of " + input[i] + " failed: ", one, bigProduct);
				}
			}
		}
	}


	/**
	 * Test method for {@link mpc.ShamirSharing#modSqrt(long)}.
	 */
	public void testModSqrt() {
		/** the max. value x for which x^2 fits into an integer */
		final long MAX_INT_ROOT = 46340;
		long computedResult = 0;
		long computedResultSquared = 0;

		for(int groupOrderIndex = 0; groupOrderIndex < groupOrders.length; groupOrderIndex++) {
			try{
				maxCoefficient = groupOrders[groupOrderIndex];
				mpcShamirSharing = new ShamirSharing();
				mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
				mpcShamirSharing.setFieldSize(maxCoefficient);
				mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
				mpcShamirSharing.init();
			}
			catch (Exception e) {
				fail("An exception occured when creating and initializing a MpcShamirSharing instance: " +Utils.getStackTrace(e));
			}
			createInputValues();

			System.out.println("\ntesting with exponent=" + maxCoefficient);
			long realResult = 0;
			for(int i = 1; i < input.length; i++) {
				/**
				 * for input values x for which x^2 fits into an integer:
				 * test if the computed result matches the real result
				 * (computed with a slow but simple/working routine)
				 * 
				 * otherwise:
				 * use the squared input s.t we know that it has a root and
				 * test that the squared root is equal to the functions input
				 */
				// check that input value is a valid group element and >0
				if((input[i] < maxCoefficient) && (input[i] > 0)) {
					if(maxCoefficient < MAX_INT_ROOT) {
						computedResult = mpcShamirSharing.modSqrt(input[i]);
						if(computedResult==0) {
							// check if the input value really has no modular square root
							realResult = modSqrt( (int)input[i] );
							assertEquals("testing modular square root of " + input[i] + " failed: ", realResult, computedResult);
						} else {
							computedResultSquared = mpcShamirSharing.modMultiply(computedResult, computedResult);
							assertEquals("testing modular square root of " + input[i] + " failed: ", input[i], computedResultSquared);
						}
					} else {
						computedResult = mpcShamirSharing.modSqrt( mpcShamirSharing.modMultiply(input[i], input[i]) );
						if(computedResult > 0) {
							computedResultSquared = mpcShamirSharing.modMultiply(computedResult, computedResult);
							assertEquals("testing modular square root of " + input[i] + " failed: ", mpcShamirSharing.modMultiply(input[i], input[i]), computedResultSquared);
						} else {
							// computation of modular square root failed
							System.out.println("computation of modular square root failed for input: "+input[i]+" computed result="+computedResult);
						}
					}
//					// check if the computed root is from the lower half (important for random bit generation operation)
//					if( !(computedResult < (maxCoefficient/2)) ) {
//						System.out.println("computed root " + computedResult +" is not from the lower half (<"+(maxCoefficient/2)+")");
//					}
				}
			}
		}
	}


	/**
	 * tries to find the modular square root of the given value
	 * this function is fairly slow and should only be used for small
	 * field sizes
	 *
	 * @param a		the value for which to find the modular square root
	 * @return		the modular square root of a if found; 0 otherwise
	 */
	private int modSqrt(int a) {
		for(int i = 0; i < maxCoefficient; i++) {
			if( ((i*i)%maxCoefficient)== a ) {
				return i;
			}
		}
		return 0;
	}


	private void createInputValues() {
		// create input values
		random = new Random();
		input = new long[2*borderSize + randomValuesCount];
		// create lower end inputs
		for(int i = 0; i < borderSize; i++) {
			input[i] = i % maxCoefficient;
		}
		// create upper end inputs
		for(int i = 0; i < borderSize; i++) {
			input[borderSize+i] = (maxCoefficient-1 - i) % maxCoefficient;
			if(input[borderSize+i] < 0) {
				input[borderSize+i] = 0-input[borderSize+i];
			}
		}
		// create random value inputs
		long randomValue = 0;
		for(int i = 0; i < randomValuesCount; i++) {
			randomValue = random.nextLong() % maxCoefficient;
			if(randomValue < 0) {
				if(randomValue != Long.MIN_VALUE) {
					randomValue = 0-randomValue;
				} else {
					randomValue = 1;
				}
			}
			input[(2*borderSize)+i] = randomValue;
		}
	}
}
