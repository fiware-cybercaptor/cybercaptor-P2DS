// Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
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
 * @author Stephan Neuhaus
 */
package ch.ethz.sepia.connections;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.cert.X509Certificate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SSLConnection implements Connection {
    private static final Logger logger = LogManager
            .getLogger(SSLConnection.class);

    /** Mask for least significant byte */
    private static final int LSB1 = 0xFF;
    private final SSLSocket socket;

    public SSLConnection(final PrivacyPeerAddress ppa, final SSLContext sslContext)
            throws UnknownHostException, IOException, NoSuchAlgorithmException {
        final String host = ppa.host;
        final int serverPort = ppa.serverPort;
        final SSLSocketFactory socketFactory = sslContext.getSocketFactory();

        this.socket = (SSLSocket) socketFactory.createSocket(
                InetAddress.getByName(host), serverPort);
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
        socket.setNeedClientAuth(true);
        socket.addHandshakeCompletedListener(event -> logger
                .info("SSL handshake is completed. Chosen ciphersuite: "
                        + event.getCipherSuite()));
        checkCertificate();
    }

    /**
     * Checks the certificate associated to an Connection.
     *
     * @param socket
     *            the SSL socket.
     */
    private void checkCertificate() {
        SSLSession session = null;
        try {
            session = socket.getSession();
            final X509Certificate[] certificates
                = session.getPeerCertificateChain();
            final Principal principal = certificates[0].getSubjectDN();
            logger.info(session.getPeerHost()
                    + " has presented a certificate belonging to ["
                    + principal.getName() + "]");
            logger.info("The certificate bears the valid signature of: ["
                    + certificates[0].getIssuerDN().getName() + "]");

        } catch (final SSLPeerUnverifiedException e) {
            logger.fatal(session.getPeerHost() + ":" + session.getPeerPort()
                    + " did not present a valid certificate. Details: "
                    + e.getMessage());
        }
    }


    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public byte[] read() throws IOException {
        final InputStream inStream = socket.getInputStream();

        // read message length
        final int expectedNumberOfBytes = read32(inStream);

        // read message
        final byte[] messageBufferTempResult = new byte[expectedNumberOfBytes];
        int beginIndex = 0;
        int lengthToRead = expectedNumberOfBytes;
        int totalBytes = 0;
        int bytesRead = 0;
        while (totalBytes < expectedNumberOfBytes) {
            bytesRead = inStream.read(messageBufferTempResult, beginIndex,
                    lengthToRead);

            if (bytesRead == -1) { // EOF
                break;
            } else {
                totalBytes += bytesRead;
                beginIndex = totalBytes;
                lengthToRead = expectedNumberOfBytes - beginIndex;
            }
        }
        assert totalBytes == expectedNumberOfBytes;

        return messageBufferTempResult;
    }

    /**
     * Reads a 32bit integer (8 bit at a time)
     *
     * @return Integer that was received
     * @throws IOException
     */
    private int read32(final InputStream inStream) throws IOException {
        int ret = 0;

        for (int i = 0; i < 4; i++) {
            ret <<= 8;

            final int t = inStream.read();
            if (t != -1) {
                ret = ret + t;
            } else {
                throw new IOException("End of inStream reached (read returned -1).");
            }
        }

        return ret;
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        final OutputStream outStream = socket.getOutputStream();
        write32(outStream, bytes.length);
        outStream.write(bytes);
        outStream.flush();
    }

    /**
     * Writes a 32bit integer (8 bit at a time)
     *
     * @param i
     *            The integer to send
     */
    private void write32(final OutputStream outStream, final int i)
            throws IOException {
        outStream.write(i >> 24 & LSB1);
        outStream.write(i >> 16 & LSB1);
        outStream.write(i >>  8 & LSB1);
        outStream.write(i >>  0 & LSB1);
    }
}
