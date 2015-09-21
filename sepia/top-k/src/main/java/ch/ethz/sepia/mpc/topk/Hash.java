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

package ch.ethz.sepia.mpc.topk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;


/**
 * Implements a cryptographic hash function with a seed, operating on integer values.
 *
 * @author Martin Burkhart
 *
 */

public class Hash {
	private static final String HASH_ALGORITHM = "SHA-1";

	private int H; // Range of hash values
	private MessageDigest digest;

	private ByteArrayOutputStream arrayOutputStream;
	private DataOutputStream dataOutputStream;

	private int seed;

	/**
	 * Creates a new hash function with a random seed.
	 * @param H the range of the hash function is [0...H-1]
	 * @throws NoSuchAlgorithmException
	 */
	public Hash(int H) throws NoSuchAlgorithmException {
		this.H=H;
		digest = MessageDigest.getInstance(HASH_ALGORITHM);
		Random rand = new Random();
		seed = rand.nextInt();

		arrayOutputStream = new ByteArrayOutputStream(8);
		dataOutputStream = new DataOutputStream(arrayOutputStream);
	}

	/**
	 * @return the seed
	 */
	public int getSeed() {
		return seed;
	}

	/**
	 * @param seed the seed to set
	 */
	public void setSeed(int seed) {
		this.seed = seed;
	}

	/**
	 * Returns the range of hash values. Values are generated in [0, ..., H-1].
	 * @return H
	 */
	public int getH() {
		return H;
	}

	/**
	 * Returns the hash of a value.
	 * @param value the value
	 * @return hash of the value
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public int getHash(int value) throws NoSuchAlgorithmException, IOException {
		dataOutputStream.writeInt(seed);
		dataOutputStream.writeInt(value);
		dataOutputStream.flush();
		byte[] input = arrayOutputStream.toByteArray();
		arrayOutputStream.reset();
		return getHash(input);
	}

	/**
	 * Returns the hash of a byte array.
	 * @param input
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	private int getHash(byte[] input) throws NoSuchAlgorithmException, IOException {
		digest.reset();
		byte[] hashbytes = digest.digest(input);

		int hash =0;
		for(int i=0; i<4; i++) {
			hash += ((int)hashbytes[i])<<(i*8);
		}

		// restrict to [0,...,H-1]
		hash = hash % H;
		if (hash < 0) {
			hash = -hash;
		}
		return (int) (hash);
	}


}
