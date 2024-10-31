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

package com.swirlds.platform.crypto;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * A serializable wrapper for an {@link X509Certificate} instance.
 */
public class SerializableX509Certificate implements SelfSerializable {

    /**
     * There is no upper bound on the size of certs.
     * A basic cert is around 1KB, with the possibility of certificate chains, this increases.
     * Our use case includes RSA self-signed certificates with an EC TLS certificate signed by the RSA cert.
     * 8KB is probably more space than needed for our use case, but better to be safe than sorry.
     */
    public static final int MAX_CERT_LENGTH = 1024 * 8;

    private static final long CLASS_ID = 0x2332728f044c1c87L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private X509Certificate certificate;

    /**
     * Constructs a new instance of {@link SerializableX509Certificate}.
     *
     * @param certificate the {@link X509Certificate} instance
     */
    public SerializableX509Certificate(@NonNull final X509Certificate certificate) {
        this.certificate = Objects.requireNonNull(certificate);
    }

    /**
     * Constructs a new instance of {@link SerializableX509Certificate} for deserialization.
     */
    public SerializableX509Certificate() {
        // empty constructor for deserialization
    }

    /**
     * Gets the {@link X509Certificate} instance.
     *
     * @return the {@link X509Certificate} instance
     */
    @NonNull
    public X509Certificate getCertificate() {
        return Objects.requireNonNull(certificate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull SerializableDataOutputStream out) throws IOException {
        try {
            final byte[] encoded = certificate.getEncoded();
            out.writeByteArray(encoded);
        } catch (CertificateEncodingException e) {
            throw new IOException("Not able to serialize x509 certificate", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull SerializableDataInputStream in, int version) throws IOException {
        final byte[] encoded = in.readByteArray(MAX_CERT_LENGTH);
        try {
            certificate = (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
        } catch (final CertificateException e) {
            throw new IOException("Not able to deserialize x509 certificate", e);
        }
    }

    /**
     * Gets the public key from the certificate.
     *
     * @return the public key
     */
    @NonNull
    public PublicKey getPublicKey() {
        return Objects.requireNonNull(certificate.getPublicKey(), "PublicKey is null");
    }
}
