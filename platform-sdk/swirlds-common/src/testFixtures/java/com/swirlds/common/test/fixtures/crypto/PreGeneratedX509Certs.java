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

package com.swirlds.common.test.fixtures.crypto;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandom;
import static com.swirlds.platform.crypto.CryptoStatic.generateKeysAndCerts;

import com.swirlds.common.crypto.SerializableX509Certificate;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * A utility class for generating and retrieving pre-generated X.509 certificates for testing purposes.
 * <p>
 * Do Not Use In Production Code
 */
public class PreGeneratedX509Certs {
    public static final String SIG_CERT_FILE =
            "../swirlds-common/src/testFixtures/resources/com/swirlds/common/test/fixtures/crypto/sigCerts.data";
    public static final String AGREE_CERT_FILE =
            "../swirlds-common/src/testFixtures/resources/com/swirlds/common/test/fixtures/crypto/agrCerts.data";

    private static final Map<NodeId, SerializableX509Certificate> sigCerts = new HashMap<>();
    private static final Map<NodeId, SerializableX509Certificate> agreeCerts = new HashMap<>();

    /**
     * Utility class
     */
    private PreGeneratedX509Certs() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Generates a set of X.509 certificates for testing purposes.
     *
     * @param numCerts the number of certificates to generate
     * @param random   the random number generator to use
     */
    public static void generateCerts(final int numCerts, @NonNull final Random random) {
        final Path sigCertPath = Path.of(SIG_CERT_FILE);
        final File sigCertFile = sigCertPath.toFile();
        if (sigCertFile.exists()) {
            sigCertFile.delete();
        }

        final Path agreeCertPath = Path.of(AGREE_CERT_FILE);
        final File agreeCertFile = agreeCertPath.toFile();
        if (agreeCertFile.exists()) {
            agreeCertFile.delete();
        }

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(numCerts).build();

        try {
            generateKeysAndCerts(addressBook);
            final FileOutputStream sigCertFos = new FileOutputStream(sigCertFile);
            final SerializableDataOutputStream sigCertOs = new SerializableDataOutputStream(sigCertFos);

            final FileOutputStream agreeCertFos = new FileOutputStream(agreeCertFile);
            final SerializableDataOutputStream agreeCertOs = new SerializableDataOutputStream(agreeCertFos);

            sigCertOs.writeInt(numCerts);
            agreeCertOs.writeInt(numCerts);
            for (final Address address : addressBook) {
                final SerializableX509Certificate sigCert =
                        new SerializableX509Certificate(Objects.requireNonNull(address.getSigCert()));
                final SerializableX509Certificate agreeCert =
                        new SerializableX509Certificate(Objects.requireNonNull(address.getAgreeCert()));
                sigCerts.put(address.getNodeId(), sigCert);
                agreeCerts.put(address.getNodeId(), agreeCert);
                sigCertOs.writeSerializable(sigCert, false);
                agreeCertOs.writeSerializable(agreeCert, false);
            }
            sigCertOs.close();
            agreeCertOs.close();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            throw new IllegalStateException("critical failure in generating certificates", e);
        }
    }

    /**
     * Generates a set of X.509 certificates for testing purposes.
     *
     * @param numCerts the number of certificates to generate
     */
    public static void generateCerts(final int numCerts) {
        generateCerts(numCerts, getRandom());
    }

    /**
     * Retrieves a pre-generated X.509 certificate for signing purposes.
     *
     * @param nodeId the node ID
     * @return the X.509 certificate
     */
    @Nullable
    public static SerializableX509Certificate getSigCert(final long nodeId) {
        if (sigCerts.isEmpty()) {
            loadCerts();
            if (sigCerts.isEmpty()) {
                return null;
            }
        }
        long index = nodeId % sigCerts.size();
        return sigCerts.get(new NodeId(index));
    }

    /**
     * Retrieves a pre-generated X.509 certificate for TLS agreement use.
     *
     * @param nodeId the node ID
     * @return the X.509 certificate
     */
    public static SerializableX509Certificate getAgreeCert(final long nodeId) {
        if (agreeCerts.isEmpty()) {
            loadCerts();
            if (agreeCerts.isEmpty()) {
                return null;
            }
        }
        long index = nodeId % agreeCerts.size();
        return agreeCerts.get(new NodeId(index));
    }

    /**
     * Loads pre-generated X.509 certificates from disk.
     */
    private static void loadCerts() {
        final Path sigCertPath = Path.of(SIG_CERT_FILE);
        final File sigCertFile = sigCertPath.toFile();
        final Path agreeCertPath = Path.of(AGREE_CERT_FILE);
        final File agreeCertFile = agreeCertPath.toFile();

        if (!sigCertFile.exists() || !agreeCertFile.exists()) {
            // the certs must be generated before they can be loaded.
            return;
        }

        try (final SerializableDataInputStream sigCertIs =
                        new SerializableDataInputStream(new FileInputStream(sigCertFile));
                final SerializableDataInputStream agreeCertIs =
                        new SerializableDataInputStream(new FileInputStream(agreeCertFile))) {
            final int numSigCerts = sigCertIs.readInt();
            for (int i = 0; i < numSigCerts; i++) {
                final SerializableX509Certificate sigCert =
                        sigCertIs.readSerializable(false, SerializableX509Certificate::new);
                sigCerts.put(new NodeId(i), sigCert);
            }

            final int numAgreeCerts = agreeCertIs.readInt();
            for (int i = 0; i < numAgreeCerts; i++) {
                final SerializableX509Certificate agreeCert =
                        agreeCertIs.readSerializable(false, SerializableX509Certificate::new);
                agreeCerts.put(new NodeId(i), agreeCert);
            }
        } catch (final Exception e) {
            throw new IllegalStateException("critical failure in loading certificates", e);
        }
        if (sigCerts.isEmpty() || agreeCerts.isEmpty()) {
            throw new IllegalStateException(
                    "critical failure in loading certificates from source files, no certs found.");
        }
    }
}
