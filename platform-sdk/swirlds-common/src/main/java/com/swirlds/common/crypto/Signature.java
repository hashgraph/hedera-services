/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import static com.swirlds.common.crypto.SignatureType.RSA;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * Encapsulates a cryptographic signature along with its SignatureType.
 */
public class Signature {
    /** a unique class type identifier */
    private static final long CLASS_ID = 0x13dc4b399b245c69L;

    /** the current serialization version */
    private static final int CLASS_VERSION = 1;

    /** The type of cryptographic algorithm used to create the signature */
    private final SignatureType signatureType;

    /** signature byte array */
    private final Bytes signatureBytes;

    public Signature(@NonNull final SignatureType signatureType, @NonNull final byte[] signatureBytes) {
        this(signatureType, Bytes.wrap(signatureBytes));
    }

    public Signature(@NonNull final SignatureType signatureType, @NonNull final Bytes signatureBytes) {
        this.signatureType = Objects.requireNonNull(signatureType, "signatureType should not be null");
        this.signatureBytes = Objects.requireNonNull(signatureBytes, "signatureBytes should not be null");
    }

    /**
     * @return the bytes of this signature in an immutable instance
     */
    @NonNull
    public Bytes getBytes() {
        return signatureBytes;
    }

    /**
     * Get the type of this signature.
     */
    @NonNull
    public SignatureType getType() {
        return signatureType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof final Signature signature)) {
            return false;
        }

        return Objects.equals(signatureBytes, signature.signatureBytes) && signatureType == signature.signatureType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(signatureType, signatureBytes);
    }

    /**
     * Deserialize a signature from a stream.
     *
     * @param in the stream to read from
     * @param readClassId whether to read the class ID from the stream
     *
     * @return the signature read from the stream
     */
    public static Signature deserialize(final SerializableDataInputStream in, final boolean readClassId)
            throws IOException {
        if (readClassId) {
            final long classId = in.readLong();
            if (classId != CLASS_ID) {
                throw new IOException("unexpected class ID: " + classId);
            }
        }
        in.readInt(); // ignore version
        final SignatureType signatureType = SignatureType.from(in.readInt(), RSA);
        final byte[] signatureBytes = in.readByteArray(signatureType.signatureLength(), true);

        return new Signature(signatureType, signatureBytes);
    }

    /**
     * Serialize this signature to a stream.
     *
     * @param out the stream to write to
     * @param withClassId whether to write the class ID to the stream
     */
    public void serialize(final SerializableDataOutputStream out, final boolean withClassId) throws IOException {
        if (withClassId) {
            out.writeLong(CLASS_ID);
        }
        out.writeInt(CLASS_VERSION);
        out.writeInt(signatureType.ordinal());
        out.writeByteArray(signatureBytes.toByteArray(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("signatureType", signatureType)
                .append("sigBytes", signatureBytes)
                .toString();
    }
}
