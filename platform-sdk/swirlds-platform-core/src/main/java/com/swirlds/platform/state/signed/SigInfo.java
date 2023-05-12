/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * One signature on a signed state, plus related info. An immutable 4-tuple of (round, member, state hash,
 * signature). It does NOT do defensive copying, so callers should be careful to not modify array elements.
 *
 * @deprecated this class is only present for legacy deserialization
 */
@Deprecated(forRemoval = true)
public class SigInfo implements FastCopyable, SelfSerializable {
    private static final long CLASS_ID = 0xea25a1f0f38a7497L;

    private static class CLassVersion {
        public static final int ORIGINAL = 1;
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
        /**
         * Instead of storing the hash/signature as raw bytes, use the Hash/Signature object.
         */
        public static final int USE_HASH_SIGNATURE_WRAPPER = 3;
    }

    /**
     * This version number should be used to handle compatibility issues that may arise from any future
     * changes to this class
     */
    private long classVersion;
    /** the signed state reflects all events with received round less than or equal to this */
    private long round;
    /** the member who signed the state */
    private long memberId;
    /** the hash of the state that was signed. The signature algorithm may internally hash this hash. */
    private Hash hash;
    /** the signature */
    private Signature signature;

    private boolean immutable;

    public SigInfo(final long round, final long memberId, final Hash hash, final Signature signature) {
        classVersion = 1;
        this.round = round;
        this.memberId = memberId;
        this.hash = hash;
        this.signature = signature;
    }

    /** the default constructor is needed for FastCopyable */
    public SigInfo() {
        classVersion = 1;
    }

    private SigInfo(final SigInfo sourceValue) {
        this.round = sourceValue.getRound();
        this.memberId = sourceValue.getMemberId();
        this.hash = sourceValue.getHash();
        this.signature = sourceValue.getSignature();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SigInfo copy() {
        throwIfImmutable();
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException("This class id deprecated. Don't use it.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        round = in.readLong();
        memberId = in.readLong();

        if (version < CLassVersion.USE_HASH_SIGNATURE_WRAPPER) {
            final byte[] hashBytes = in.readByteArray(DigestType.SHA_384.digestLength());
            if (hashBytes != null) {
                hash = new Hash(hashBytes, DigestType.SHA_384);
            }

            final byte[] signatureBytes = in.readByteArray(SignatureType.RSA.signatureLength());
            if (signatureBytes != null) {
                signature = new Signature(SignatureType.RSA, signatureBytes);
            }

        } else {
            hash = in.readSerializable(false, Hash::new);
            signature = in.readSerializable(false, Signature::new);
        }
    }

    /**
     * getter for the signed state round that reflects all events received
     *
     * @return round
     */
    public long getRound() {
        return round;
    }

    /**
     * getter for the member who signed the state
     *
     * @return member who signed the state
     */
    public long getMemberId() {
        return memberId;
    }

    /**
     * getter for the the hash of the state that was signed
     *
     * @return the hash of the state that was signed
     */
    public Hash getHash() {
        return hash;
    }

    /**
     * getter for the signature
     *
     * @return the signature
     */
    public Signature getSignature() {
        return signature;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SigInfo sigInfo = (SigInfo) o;

        return new EqualsBuilder()
                .append(round, sigInfo.round)
                .append(memberId, sigInfo.memberId)
                .append(hash, sigInfo.hash)
                .append(signature, sigInfo.signature)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(round)
                .append(memberId)
                .append(hash)
                .append(signature)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("classVersion", classVersion)
                .append("round", round)
                .append("memberId", memberId)
                .append("hash", hash)
                .append("sig", signature)
                .toString();
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
    public int getVersion() {
        return CLassVersion.USE_HASH_SIGNATURE_WRAPPER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return CLassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return this.immutable;
    }
}
