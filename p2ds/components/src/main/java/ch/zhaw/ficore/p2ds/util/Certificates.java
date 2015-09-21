/* Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.zhaw.ficore.p2ds.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

public class Certificates {

    /** The logger. */
    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(Certificates.class));

    public static byte[] decodeBase64(final String input) {
        return Base64.decodeBase64(input.getBytes());
    }

    public static String encodeBase64(final byte[] input) {
        return Base64.encodeBase64String(input);
    }

    public static Certificate getCertificate(final byte[] input) {
        ByteArrayInputStream bias = new ByteArrayInputStream(input);
        BufferedInputStream bis = new BufferedInputStream(bias);

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            while (bis.available() > 0) {
                Certificate cert = cf.generateCertificate(bis);
                System.out.println(cert.toString());
                return cert;
            }
        } catch (Exception e) {
            LOGGER.catching(e);
            return null;
        }

        return null;
    }

    public static Certificate getCertificate(final String input) {
        return getCertificate(decodeBase64(input));
    }

    public static boolean isValidPublicKey(final byte[] input) {
        ByteArrayInputStream bias = new ByteArrayInputStream(input);
        new BufferedInputStream(bias);

        try {
            Provider provider = new BouncyCastleProvider();
            byte[] pubKeyData = input;
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyData);
            KeyFactory keyFactory = KeyFactory.getInstance("EC", provider);
            keyFactory.generatePublic(pubKeySpec);
        } catch (Exception e) {
            LOGGER.catching(e);
            return false;
        }

        return true;
    }

    public static boolean isValidPublicKey(final String input) {
        return isValidPublicKey(decodeBase64(input));
    }
}
