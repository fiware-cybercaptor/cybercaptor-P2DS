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

package ch.ethz.sepia.protocolPrimitives;

import ch.ethz.sepia.protocolPrimitives.operations.OperationsTest;


/**
 * Test cases for MpcShamirSharingProtocolPrimitives.
 *
 * @author Dilip Many
 *
 */
public class ShamirSharingProtocolPrimitivesTest extends OperationsTest {
	/**
	 * This is a combined test for the functions getBits and computeNumber.
	 * Test method for {@link mpc.protocolPrimitives.Primitives#getBits(long)}.
	 * Test method for {@link mpc.protocolPrimitives.Primitives#computeNumber(long[])}.
	 * <p>
	 * It first uses getBits to retrieve the bits of a number and then uses computeNumber to compute
	 * the number of the bits and test if the computed number is equal to the original input.
	 */
	public void testGetBitsAndComputeNumber() {
		for(int fieldSizeIndex = 0; fieldSizeIndex < fieldSizes.length; fieldSizeIndex++) {
			fieldSize = fieldSizes[fieldSizeIndex];
			initializeMpcShamirSharingInstances();
			initializeMpcShamirSharingProtocolPrimitives();
			createInputValues();
			System.out.println("\ntesting getBits and computeNumber with field size=" + fieldSize);

			long[] retrievedBits = null;
			long computedNumber = 0;
			for(int inputIndex = 0; inputIndex < input.length; inputIndex++) {
				retrievedBits = primitives[0].getBits(input[inputIndex]);
				computedNumber = primitives[0].computeNumber(retrievedBits);
				assertEquals("computed number isn't equal: ", input[inputIndex], computedNumber);
			}
		}
	}
}
