/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Signatures of the hash of a state.
 */
public class SigSet implements FastCopyable, Iterable<Long /* signing node ID */>, SelfSerializable {
    private static final long CLASS_ID = 0x756d0ee945226a92L;

    /**
     * The maximum allowed signature count. Used to prevent serialization DOS attacks.
     */
    public static final int MAX_SIGNATURE_COUNT = 1024;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
        public static final int CLEANUP = 3;
    }

    private final Map<Long /* node ID */, Signature> signatures = new HashMap<>();

    /**
     * Zero arg constructor.
     */
    public SigSet() {}

    /**
     * Copy constructor.
     *
     * @param that the sig set to copy
     */
    private SigSet(final SigSet that) {
        this.signatures.putAll(that.signatures);
    }

    /**
     * Add a signature to the sigset. Does not validate the signature.
     *
     * @param nodeId    the ID of the node that provided the signature
     * @param signature the signature to add
     */
    public void addSignature(final long nodeId, final Signature signature) {
        throwArgNull(signature, "signature");
        signatures.put(nodeId, signature);
    }

    /**
     * Remove a signature from the sigset.
     *
     * @param nodeId the ID of the signature to remove
     */
    public void removeSignature(final long nodeId) {
        signatures.remove(nodeId);
    }

    /**
     * Get the signature for the given node ID, or null if there is no signature for the requested node.
     *
     * @param nodeId the ID of the node
     * @return a signature for the node, or null if there is no signature for the node
     */
    public Signature getSignature(final long nodeId) {
        return signatures.get(nodeId);
    }

    /**
     * Check if this sigset has a signature for a given node.
     *
     * @param nodeId the node ID in question
     * @return true if a signature from this node is present
     */
    public boolean hasSignature(final long nodeId) {
        return signatures.containsKey(nodeId);
    }

    /**
     * Get an iterator that walks over the set of nodes that have signed the state.
     */
    @Override
    public Iterator<Long> iterator() {
        final Iterator<Long> iterator = signatures.keySet().iterator();

        // Wrap the iterator so that it can't be used to modify the SigSet.
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Long next() {
                return iterator.next();
            }
        };
    }

    /**
     * Get a list of all signing nodes.
     *
     * @return a list of all signing nodes
     */
    public List<Long> getSigningNodes() { // TODO test
        return new ArrayList<>(signatures.keySet());
    }

    /**
     * Get the number of signatures.
     *
     * @return the number of signatures
     */
    public int size() {
        return signatures.size();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public SigSet copy() {
        return new SigSet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(signatures.size());

        final List<Long> sortedIds = new ArrayList<>(signatures.size());
        signatures.keySet().stream().sorted().forEachOrdered(sortedIds::add);

        for (final Long nodeId : sortedIds) {
            out.writeLong(nodeId);
            out.writeSerializable(signatures.get(nodeId), false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        if (version == ClassVersion.MIGRATE_TO_SERIALIZABLE) {
            final int numMembers = in.readInt();

            final SigInfo[] sigInfoArr = in.readSerializableArray(SigInfo[]::new, numMembers, false, SigInfo::new);

            for (final SigInfo sigInfo : sigInfoArr) {
                if (sigInfo != null) {
                    signatures.put(sigInfo.getMemberId(), sigInfo.getSignature());
                }
            }
            return;
        }

        final int signatureCount = in.readInt();
        if (signatureCount > MAX_SIGNATURE_COUNT) {
            throw new IOException(
                    "Signature count of " + signatureCount + " exceeds maximum of " + MAX_SIGNATURE_COUNT);
        }

        for (int index = 0; index < signatureCount; index++) {
            final long nodeId = in.readLong();
            final Signature signature = in.readSerializable(false, Signature::new);
            signatures.put(nodeId, signature);
        }
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
        return ClassVersion.CLEANUP;
    }
}
