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
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.SystemTransactionType;
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
    }

    /**
     * signature of signed state
     */
    private Signature stateSignature;

    /**
     * the hash that was signed
     */
    private Hash stateHash;

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
     */
    public StateSignatureTransaction(final long round, final Signature stateSignature, final Hash stateHash) {
        this.stateSignature = throwArgNull(stateSignature, "stateSignature");
        this.stateHash = throwArgNull(stateHash, "stateHash");
        this.round = round;
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {

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
        return ClassVersion.ADDED_SELF_HASH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedLength() {
        return getArraySerializedLength(stateSignature.getSignatureBytes())
                + getArraySerializedLength(stateHash == null ? new byte[0] : stateHash.getValue())
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
                && Objects.equals(stateHash, that.stateHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(stateSignature, stateHash, round);
    }
}
