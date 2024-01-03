/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.roster.legacy;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.roster.RosterEntry;
import com.swirlds.platform.system.address.Address;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * An {@link Address} wrapper that implements the {@link RosterEntry} interface.
 */
public class AddressRosterEntry implements RosterEntry {

    private static final long CLASS_ID = 0x4e700e352be188aaL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final int ENCODED_CERT_MAX_SIZE = 8192;

    private Address address;
    private X509Certificate sigCert;

    /**
     * Constructs a new {@link AddressRosterEntry} from the given {@link Address} and {@link KeysAndCerts}.
     *
     * @param address      the address
     * @param keysAndCerts the keys and certs containing the signing certificate
     */
    public AddressRosterEntry(@NonNull final Address address, @NonNull final KeysAndCerts keysAndCerts) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(keysAndCerts);

        this.address = address;
        this.sigCert = keysAndCerts.sigCert();
    }

    /**
     * Empty constructor for deserialization.
     */
    public AddressRosterEntry() {}

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
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(address, false);
        try {
            out.writeByteArray(sigCert.getEncoded());
        } catch (final CertificateEncodingException e) {
            throw new IOException("Could not encode certificate", e);
        }
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        address = in.readSerializable(false, Address::new);
        final byte[] encodedCert = in.readByteArray(ENCODED_CERT_MAX_SIZE);
        try {
            sigCert = (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encodedCert));
        } catch (final CertificateException e) {
            throw new IOException("Could not decode certificate", e);
        }
    }

    @Override
    @NonNull
    public NodeId getNodeId() {
        return address.getNodeId();
    }

    @Override
    public long getWeight() {
        return address.getWeight();
    }

    @NonNull
    @Override
    public String getHostname() {
        return Objects.requireNonNullElse(address.getHostnameExternal(), "");
    }

    @Override
    public int getPort() {
        return address.getPortExternal();
    }

    @NonNull
    @Override
    public X509Certificate getSigningCertificate() {
        return sigCert;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AddressRosterEntry that = (AddressRosterEntry) o;
        return Objects.equals(address, that.address) && Objects.equals(sigCert, that.sigCert);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, sigCert);
    }

    @Override
    public String toString() {

        return new ToStringBuilder(this)
                .append("address", address)
                .append("sigCert", sigCert)
                .toString();
    }
}
