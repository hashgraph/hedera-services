/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.addressbook;

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.config.data.NodesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

/**
 * Utility class that provides static methods and constants to facilitate the Address Book Services functions.
 */
public class AddressBookHelper {

    private AddressBookHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Get the next Node ID number from the ReadableNodeStore.
     * @param nodeStore the ReadableNodeStore
     * @return nextNodeId the next Node ID
     */
    public static long getNextNodeID(@NonNull final ReadableNodeStore nodeStore) {
        requireNonNull(nodeStore);
        final long maxNodeId = StreamSupport.stream(
                        Spliterators.spliterator(nodeStore.keys(), nodeStore.sizeOfState(), DISTINCT), false)
                .mapToLong(EntityNumber::number)
                .max()
                .orElse(-1L);
        return maxNodeId + 1;
    }

    /**
     * Write the Certificate to a pem file.
     * @param pemFile to write
     * @param x509Encoding Certificate encoded byte[]
     * @throws IOException if an I/O error occurs while writing the file
     */
    public static void writeCertificatePemFile(@NonNull final Path pemFile, @NonNull final byte[] x509Encoding)
            throws IOException {
        writeCertificatePemFile(x509Encoding, new FileOutputStream(pemFile.toFile()));
    }

    /**
     * Given an X509 encoded certificate, writes it as a PEM to the given output stream.
     *
     * @param x509Encoding the X509 encoded certificate
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs while writing the PEM
     */
    public static void writeCertificatePemFile(@NonNull final byte[] x509Encoding, @NonNull final OutputStream out)
            throws IOException {
        requireNonNull(x509Encoding);
        requireNonNull(out);
        try (final var writer = new OutputStreamWriter(out);
                final PemWriter pemWriter = new PemWriter(writer)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", x509Encoding));
        }
    }

    /**
     * Read from a Certificate pem file.
     * @param pemFile the file to read from
     * @return the X509Certificate
     * @throws IOException if an I/O error occurs while reading the file
     * @throws CertificateException if the file does not contain a valid X509Certificate
     */
    public static X509Certificate readCertificatePemFile(@NonNull final Path pemFile)
            throws IOException, CertificateException {
        return readCertificatePemFile(Files.newInputStream(pemFile));
    }

    /**
     * Reads a PEM-encoded X509 certificate from the given input stream.
     * @param in the input stream to read from
     * @return the X509Certificate
     * @throws IOException if an I/O error occurs while reading the certificate
     * @throws CertificateException if the file does not contain a valid X509Certificate
     */
    public static X509Certificate readCertificatePemFile(@NonNull final InputStream in)
            throws IOException, CertificateException {
        requireNonNull(in);
        Object entry;
        try (final var parser = new PEMParser(new InputStreamReader(in))) {
            while ((entry = parser.readObject()) != null) {
                if (entry instanceof X509CertificateHolder ch) {
                    return new JcaX509CertificateConverter().getCertificate(ch);
                } else {
                    throw new CertificateException(
                            "Not X509 Certificate, it is " + entry.getClass().getSimpleName());
                }
            }
        }
        throw new CertificateException("No X509 Certificate found in the PEM file");
    }

    /**
     * Get Path of a resources file.
     * @param resourceFileName the file name
     * @return the Path
     */
    public static Path loadResourceFile(String resourceFileName) {
        return Path.of(
                Objects.requireNonNull(AddressBookHelper.class.getClassLoader().getResource(resourceFileName))
                        .getPath());
    }

    /**
     * Check DAB enable flag.
     * @param feeContext the Fee context
     */
    public static void checkDABEnabled(@NonNull final FeeContext feeContext) {
        final var nodeConfig = requireNonNull(feeContext.configuration()).getConfigData(NodesConfig.class);
        validateTrue(nodeConfig.enableDAB(), NOT_SUPPORTED);
    }
}
