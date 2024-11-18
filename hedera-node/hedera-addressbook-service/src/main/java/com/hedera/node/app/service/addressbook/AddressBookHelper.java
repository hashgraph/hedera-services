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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

/**
 * Utility class that provides static methods and constants to facilitate the Address Book Services functions.
 */
public class AddressBookHelper {
    public static final String NODES_KEY = "NODES";

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
     * @param encodes Certificate encoded byte[]
     * @throws IOException if an I/O error occurs while writing the file
     */
    public static void writeCertificatePemFile(@NonNull final Path pemFile, @NonNull final byte[] encodes)
            throws IOException {
        Objects.requireNonNull(pemFile, "pemFile must not be null");
        Objects.requireNonNull(encodes, "cert must not be null");

        final PemObject pemObj = new PemObject("CERTIFICATE", encodes);
        try (final var f = new FileOutputStream(pemFile.toFile());
                final var out = new OutputStreamWriter(f);
                final PemWriter writer = new PemWriter(out)) {
            writer.writeObject(pemObj);
        }
    }

    /**
     * Read from a Certificate pem file.
     * @param pemFile the file to read from
     * @return the X509Certificate
     * @throws IOException if an I/O error occurs while reading the file
     * @throws CertificateException if the file does not contain a valid X509Certificate
     */
    @Nullable
    public static X509Certificate readCertificatePemFile(@NonNull final Path pemFile)
            throws IOException, CertificateException {
        Objects.requireNonNull(pemFile, "pemFile must not be null");
        X509Certificate cert = null;
        Object entry;
        PEMParser parser = null;
        try {
            final var in = new InputStreamReader(Files.newInputStream(pemFile), StandardCharsets.UTF_8);
            parser = new PEMParser(in);
            if ((entry = parser.readObject()) != null) {
                if (entry instanceof X509CertificateHolder ch) {
                    cert = new JcaX509CertificateConverter().getCertificate(ch);
                } else {
                    throw new CertificateException(
                            "Not X509 Certificate, it is " + entry.getClass().getSimpleName());
                }
            }
        } catch (PEMException e) {
            throw new CertificateException("Can not read the certificate from the file " + pemFile.getFileName(), e);
        } finally {
            if (parser != null) parser.close();
        }
        return cert;
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
