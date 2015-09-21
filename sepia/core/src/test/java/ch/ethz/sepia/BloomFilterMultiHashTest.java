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

/**
 *
 */
package ch.ethz.sepia;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetAddress;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import junit.framework.TestCase;
import ch.ethz.sepia.services.BloomFilter;
import ch.ethz.sepia.services.MultiHash;


/**
 * JUnit test for Bloomfilter and Multihash classes
 * @author Manuel Widmer
 *
 */
public class BloomFilterMultiHashTest extends TestCase {

	static final private File IPv4 = new File("ipv4.txt");
	static final private int Itemcount = 10000;

	public BloomFilterMultiHashTest(String name) {
		super(name);
	}

	/**
	 * Generates random IPv4 addresses and writes them to the specified output file.
	 * There is one address per line.
	 * @param count	Number of IP addresses to generate
	 * @param outputfile Name of the output file, specify null if you don't want any output
	 * @return array of string representations of the generated IPs
	 */
	public static String generateIPv4(int count, File outputfile){
		String res = "";
		try{
			FileWriter out = null;
			InetAddress addr = null;
			if(outputfile != null){
				out = new FileWriter(outputfile);
			}
			Random m = new Random();
			for(int i = 0; i < count; i++){
				addr = InetAddress.getByAddress(IntToByteArr(m.nextInt()));
				res = addr.getHostAddress();
				if(outputfile != null){
					out.write(res + "\n");
				}
			}
			if(outputfile != null){
				out.close();
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		return res;
	}

	/**
	 * Generates random IPv6 addresses and writes them to the specified output file.
	 * There is one address per line.
	 * @param count	Number of IP addresses to generate
	 * @param outputfile Name of the output file, specify null if you don't want any output
	 * @return array of string representations of the generated IPs
	 */
	public static String generateIPv6(int count, String outputfile){
		String res = null;
		try{
			FileWriter out = null;
			InetAddress addr = null;
			if(outputfile != null){
				out = new FileWriter(outputfile);
			}
			Random m = new Random();
			for(int i = 0; i < count; i++){
				byte [] myIPv6 = new byte[16];
				for(int j = 0; j < 4; j++){
					System.arraycopy(IntToByteArr(m.nextInt()), 0, myIPv6, 4*j, 4);
				}
				addr = InetAddress.getByAddress(myIPv6);
				res = addr.getHostAddress();
				if(outputfile != null){
					out.write(res + "\n");
				}
			}
			if(outputfile != null){
				out.close();
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		return res;
	}

	/**
	 * Converts an integer to a byte array
	 * @param val value to convert
	 * @return [0]..[3] MSB to LSB representation of val
	 */
    public static byte[] IntToByteArr(int val) {
        byte[] buffer = new byte[4];

        // >>> is unsigned  and >> signed bitshift
        buffer[0] = (byte) (val >>> 24);
        buffer[1] = (byte) (val >>> 16);
        buffer[2] = (byte) (val >>> 8);
        buffer[3] = (byte) val;

        return buffer;
    }


	/**
	 * testcase for multihash
	 */
	public static void testHash() throws Exception{
		Random r = new Random();
		for(int k = 0; k < 20; k++) {
			int size = r.nextInt(100000000);
			MultiHash mh = new MultiHash(k, 1+size);
			MultiHash mh2 = new MultiHash(k, 1+size);

			FileReader fr = new FileReader("ipv4.txt");
			BufferedReader br = new BufferedReader(fr);
			String adr = null;
			int [] hashresult = null;
			int [] hashresult2 = null;
			//read file to the end
			while(null != (adr = br.readLine())){
				adr = br.readLine();
				hashresult = mh.hash(adr);
				hashresult2 = mh2.hash(adr);
				for(int j = 0; j < hashresult.length; j++){
					assertTrue("hashes not equal!",  hashresult[j] ==  hashresult2[j]);
					assertTrue("hash too large", hashresult[j] < mh.getRange());
					if(hashresult[j] >= mh.getRange()){
						System.out.println("Expected: "+ mh.getRange()+", Actual: "+hashresult[j]);
					}
					assertTrue("hash smaller than 0", hashresult[j] > 0);
				}
			}
			br.close();
			fr.close();
		}

	}

    /**
     * Only for debugging purpose
     * @param hash value to print, hash.length must be multiple of 2
     */
    public static void printHash(byte [] hash){
    	for(int i = 0; i < hash.length/2; i++){
    		int addr = (0x0000ff00&(hash[2*i]<<8)) + (0x000000ff & hash[2*i + 1]);
    		System.out.print(addr + "  ");
    	}
    	System.out.println();
    }

    /**
     * testcase for the BloomFilter class
     */
    public static void testBloomFilter() throws Exception {
		Random r = new Random();
		for(int k = 0; k < 30; k++) {
			// number of hash functions and range should not be 0
			BloomFilter bf = new BloomFilter(1 + r.nextInt(30), 2 + r.nextInt(10000000), r.nextBoolean());
			FileReader fr = new FileReader("ipv4.txt");
			BufferedReader br = new BufferedReader(fr);
			String adr = null;
			//read file to the end
			while(null != (adr = br.readLine())){
				adr = br.readLine();
				assertEquals("Removed inexistent element", false, bf.remove(adr));
				// debug info
//				if(bf.remove(adr)){
//					System.out.println("Range: " + bf.getRange() + ", Hashcount: " + bf.getHashCount());
//				}
				bf.insert(adr);
				assertEquals("Element-check failed", true, bf.check(adr));
				if(bf.isCounting()){
					assertEquals("Removing element failed", true, bf.remove(adr));
				}
			}
			br.close();
			fr.close();
		}
	}

    /**
     * testcase for the cardinality estimation
     */
    public static void testBFcardinality() throws Exception {
    	for(int i = 0; i < 50; i++){
    		Random m = new Random();
    		int insert = m.nextInt(Itemcount);
    		double [] est = BloomFilter.getParameterEstimate(insert, 0.01);
    		// debug info
    		System.out.println("Items: "+insert+", Suggested size: " + est[0] + ", Hashes: " + est[1] + ", fpr: " + est[2]);

    		BloomFilter bfnc = new BloomFilter((int)est[1],(int)est[0],false);
    		BloomFilter bfc = new BloomFilter((int)est[1],(int)est[0],true);

    		FileReader fr = new FileReader(IPv4);
    		BufferedReader br = new BufferedReader(fr);

    		String adr = null;
    		for(int k = 0; k < insert; k++){
    			adr = br.readLine();
    			bfc.insert(adr);
    			bfnc.insert(adr);
    		}

    		br.close();
    		fr.close();
    		//double pot = Math.pow((1-1.0/bf.getRange()),(double)insert*bf.getHashCount());
    		//double expectedNZ = bf.getRange()*(1-pot);


    		// check whether estimated elements are within reasonable
    		// range to number of really inserted elements
    		double card = BloomFilter.getCardinality(bfnc.getNonZeros(),
    							bfnc.getRange(), bfnc.getHashCount(), bfnc.isCounting());
    		double criticalVal = 0.02;
    		//debug
    		if(Math.abs(card -(double)insert)/insert > criticalVal){
    			System.out.println("Range: "+ bfnc.getRange() +", Hashes: "+bfnc.getHashCount());
        		System.out.println("items: "+insert +", estimate: "+card);
    		}
    		assertTrue("Non counting BF: Deviation above "+(100*criticalVal)+"%",
    				Math.abs(card -(double)insert)/insert < criticalVal);

    		card = (int)BloomFilter.getCardinality(bfc.getNonZeros(),
					bfc.getRange(), bfc.getHashCount(), bfc.isCounting());
    		assertEquals("Counting BF cardinality not correct", (int)card, insert);

    		//		System.out.println("Number of Elements: " + insert + ", Collisions: " + count);
    		//		System.out.println("Estimated Elements: " + (int)bf.getCardinality());
    		//		System.out.println("Estimated Nonzeros: " + expectedNZ);
    		//		System.out.println("Number of Nonzeros: " + bf.getNonZeros());
    	}
    }


    /**
     * First manual implementation of a BloomFilter structure
     * @throws Exception
     */
    public void snippetManualBloom() throws Exception {
    	// evaluation of bloomfilter and hash functions
		// some constants
		final int ELEMTS = (int)Math.pow(2, 20);
		final int NR_HASH = 5;
		final int FPR = 4; // actual fpr: 10^(-FPR)

		//approximation of bloomfilter size  2^24 elements
		boolean [] bloomip4 = new boolean[ELEMTS*FPR*4];
//		boolean [] bloomip6 = new boolean[ELEMTS*FPR*4];

        // Generate secret key for HMAC-MD5
        KeyGenerator kg = KeyGenerator.getInstance("HmacMD5");
        SecretKey [] sk = new SecretKey[NR_HASH];
        // Get instance of Mac object implementing HMAC-MD5, and
        // initialize it with the above secret key
        Mac [] mac  = new Mac[NR_HASH];
        for(int i =0; i< NR_HASH;i++){
        	mac[i] = Mac.getInstance("HmacMD5");
        	sk[i] = kg.generateKey();

        	mac[i].init(sk[i]);
        }

        FileReader rr = new FileReader(IPv4);
        BufferedReader in = new BufferedReader(rr);

        // read ip addresses from file and hash them to bloom filter, measure time
        long time = System.currentTimeMillis();
        // unused: int fprcount = 0;
        for(int i=0; i < 100000; i++){
        	/*readIP(i,IPv4).getBytes()*/
        	String nextIP = in.readLine();
        	byte[] result = mac[0].doFinal(nextIP.getBytes()); //16 Bytes hash
        	byte[] result2 = mac[1].doFinal(nextIP.getBytes()); //16 Bytes hash
        	//printHash(result);
        	// use 2 bytes to address bloomfilter
        	int addr;
        	//int collision = 0;
        	for(int k = 0; k < 5; k++){
        		// create index and check
        		addr = (0x00ff0000 & (result[3*k+2]<<16))
        			 + (0x0000ff00 & (result[3*k+1]<<8))
        			 + (0x000000ff & result[3*k]);
        		//System.out.println(addr);
        		if(bloomip4[addr]){
        			//collision++;
        		}else{
        			bloomip4[addr] = true;
        		}
        	}
        	for(int k = 0; k < 8; k++){
        		// create index and check
        		addr = (0x0000ff00 &(result2[2*k]<<8)) + (0x000000ff & result2[2*k+1]);
        		//System.out.println(addr);
        		if(bloomip4[addr]){
        			//collision++;
        		}else{
        			bloomip4[addr] = true;
        		}
        	}

        	//if(collision > 10){
        	//	fprcount++;
        	//}
        	//readIP(i,IPv6);
        }
        time = System.currentTimeMillis() - time;
		//System.out.println("Time to hash and insert IPs: "+time/1000.0);
		//System.out.println("Number of collisions: "+fprcount);

		in.close();
		rr.close();
    }

   /*
    * (non-Javadoc)
    * @see junit.framework.TestCase#setUp()
    */
    public void setUp() {
    	generateIPv4(Itemcount, IPv4);
    }

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#tearDown()
     */
    public void tearDown(){
    	IPv4.delete();
    }

}
