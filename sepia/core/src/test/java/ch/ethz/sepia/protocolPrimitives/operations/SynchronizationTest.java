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

import java.util.Arrays;
import java.util.Random;

import ch.ethz.sepia.mpc.protocolPrimitives.operations.Synchronization;

/**
 * Test cases for the Synchronization operation.
 * 
 * @author Martin Burkhart
 */
public class SynchronizationTest extends OperationsTest {
	/** [privacyPeer][run][position] */
	long[][][] inputValues;
	protected static final int NR_OF_RUNS = 100;

	/**
	 * Test method for {@link Synchronization}.
	 */
	public void testLossySynchronization() {
		// Field sizes are not really relevant for this operation. Pick the last
		// one.
		fieldSize = fieldSizes[fieldSizes.length - 1];

		initializeMpcShamirSharingInstances();
		initializeMpcShamirSharingProtocolPrimitives();

		createInputValues();

		System.out.println("\ntesting synchronization operation with group order=" + fieldSize);

		// synchronize inputs of the privacy peers
		for (int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			primitives[privacyPeerIndex].initialize(NR_OF_RUNS);
			for (int r = 0; r < NR_OF_RUNS; r++) {
				long[] data = inputValues[privacyPeerIndex][r];
				primitives[privacyPeerIndex].synchronize(r, data);
			}
		}
		// Now pick the failing PP
		Random rand = new Random();
		int failingPP = rand.nextInt(numberOfPrivacyPeers);
		doLossyOperation(null, failingPP, true);

		// get synchronization results
		long[][][] synchronizationResults = new long[numberOfPrivacyPeers][NR_OF_RUNS][randomValuesCount];

		for (int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
			for (int r = 0; r < NR_OF_RUNS; r++) {
				synchronizationResults[privacyPeerIndex][r]=primitives[privacyPeerIndex].getResult(r);
			}
		}
		
		// Verify synchronization results
		for(int r=0; r<NR_OF_RUNS; r++) {
			long[] realResult = new long[randomValuesCount];
			for(int i=0; i<randomValuesCount; i++) {
				realResult[i]=1;
			}
			
			for (int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				if (privacyPeerIndex==failingPP) {
					continue;
				}
				for (int position = 0; position<randomValuesCount; position++) {
					realResult[position] &= inputValues[privacyPeerIndex][r][position];
				}
			}
			
			for (int privacyPeerIndex = 0; privacyPeerIndex < numberOfPrivacyPeers; privacyPeerIndex++) {
				if (privacyPeerIndex==failingPP) {
					continue;
				}
				boolean arraysEqual = Arrays.equals(realResult, synchronizationResults[privacyPeerIndex][r]);
				assertEquals("Synchronization result for PP"+privacyPeerIndex+" not correct. Expected: "+Arrays.toString(realResult)+" Computed: "+Arrays.toString(synchronizationResults[privacyPeerIndex][r]), true, arraysEqual);
			}
			
		}
		
	}

	@Override
	protected void createInputValues() {
		inputValues = new long[numberOfPrivacyPeers][NR_OF_RUNS][randomValuesCount];
		Random rand = new Random();
		for (int pp = 0; pp < numberOfPrivacyPeers; pp++) {
			for (int r = 0; r < NR_OF_RUNS; r++) {
				for (int position = 0; position < randomValuesCount; position++) {
					// generate random 0/1 values with a 20%/80% distribution.
					if (rand.nextInt(10) >= 2) {
						inputValues[pp][r][position] = 1;
					}
				}
			}
		}
	}
}
