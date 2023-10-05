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

package com.swirlds.common.system.transaction.internal;

import static com.swirlds.common.io.streams.AugmentedDataOutputStream.getArraySerializedLength;
import static com.swirlds.common.system.transaction.SystemTransactionType.SYS_TRANS_STATE_SIG;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.SystemTransactionType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * Every round, the signature of a signed state is put in this transaction
 * and gossiped to other nodes
 */
public final class StateSignatureTransaction extends SystemTransaction {

    /**
     * class identifier for the purposes of serialization
     */
    private static final long CLASS_ID = 0xaf7024c653caabf4L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int ADDED_SELF_HASH = 2;
        public static final int ADDED_EPOCH_HASH = 3;
    }

    /**
     * signature of signed state
     */
    private Signature stateSignature;

    /**
     * the hash that was signed
     */
    private Hash stateHash;

    /** the hash of the epoch to which this signature corresponds to */
    private Hash epochHash;

    /**
     * round number of signed state
     */
    private long round = 0;

    /**
     * No-argument constructor used by ConstructableRegistry
     */
    public StateSignatureTransaction() {}

    /**
     * Create a state signature transaction
     *
     * @param round
     * 		The round number of the signed state that this transaction belongs to
     * @param stateSignature
     * 		The byte array of signature of the signed state
     * @param stateHash
     *      The hash that was signed
     * @param epochHash
     *      The hash of the epoch to which this signature corresponds to
     */
    public StateSignatureTransaction(
            final long round,
            @NonNull final Signature stateSignature,
            @NonNull final Hash stateHash,
            @Nullable final Hash epochHash) {
        this.round = round;
        this.stateSignature = Objects.requireNonNull(stateSignature, "stateSignature must not be null");
        this.stateHash = Objects.requireNonNull(stateHash, "stateHash must not be null");
        this.epochHash = epochHash;
    }

    /**
     * Same as {@link #StateSignatureTransaction(long, Signature, Hash, Hash)} but with epochHash set to null
     */
    public StateSignatureTransaction(
            final long round, @NonNull final Signature stateSignature, @NonNull final Hash stateHash) {
        this(round, stateSignature, stateHash, null);
    }

    /**
     * @return the round number of the signed state that this transaction belongs to
     */
    public long getRound() {
        return round;
    }

    /**
     * @return the signature on the state
     */
    public Signature getStateSignature() {
        return stateSignature;
    }

    /**
     * @return the hash that was signed
     */
    public Hash getStateHash() {
        return stateHash;
    }

    /**
     * @return the hash of the epoch to which this signature corresponds to
     */
    public @Nullable Hash getEpochHash() {
        return epochHash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return getSerializedLength();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SystemTransactionType getType() {
        return SYS_TRANS_STATE_SIG;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeByteArray(stateSignature.getSignatureBytes());

        // state hash will only be null for objects originally serialized with version ORIGINAL
        if (stateHash == null) {
            out.writeByteArray(new byte[0]);
        } else {
            out.writeByteArray(stateHash.getValue());
        }
        out.writeLong(round);
        out.writeSerializable(epochHash, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {

        if (version == ClassVersion.ORIGINAL) {
            in.readBoolean();
        }

        stateSignature = new Signature(SignatureType.RSA, in.readByteArray(SignatureType.RSA.signatureLength()));

        // state hash will only be null for objects originally serialized with version ORIGINAL
        if (version >= ClassVersion.ADDED_SELF_HASH) {
            final byte[] hashBytes = in.readByteArray(DigestType.SHA_384.digestLength());
            if (hashBytes.length != 0) {
                stateHash = new Hash(hashBytes, DigestType.SHA_384);
            }
        }

        round = in.readLong();
        if (version >= ClassVersion.ADDED_EPOCH_HASH) {
            epochHash = in.readSerializable(false, Hash::new);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
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
        return ClassVersion.ADDED_EPOCH_HASH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedLength() {
        return getArraySerializedLength(stateSignature.getSignatureBytes())
                + getArraySerializedLength(stateHash == null ? new byte[0] : stateHash.getValue())
                + SerializableDataOutputStream.getInstanceSerializedLength(epochHash, true, false)
                + Long.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StateSignatureTransaction that = (StateSignatureTransaction) o;
        return round == that.round
                && Objects.equals(stateSignature, that.stateSignature)
                && Objects.equals(stateHash, that.stateHash)
                && Objects.equals(epochHash, that.epochHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(stateSignature, stateHash, round, epochHash);
    }
}
