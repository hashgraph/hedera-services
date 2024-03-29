/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * Used during the synchronization protocol to send data needed to reconstruct a single virtual node.
 *
 * <p>The teacher sends one response for every {@link PullVirtualTreeRequest} received from the
 * learner. Every response includes a path followed by an integer flag that indicates if the node
 * is clear (value 0, node hash on the teacher is the same as sent by the learner), or not (non-zero
 * value). If the path corresponds to a leaf node, and the node is not clear, a {@link
 * com.swirlds.virtualmap.datasource.VirtualLeafRecord} for the node is included in the end of the
 * response.
 */
public class PullVirtualTreeResponse implements SelfSerializable {

    private static final long CLASS_ID = 0xecfbef49a90334e3L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Only used on the teacher side
    private final VirtualTeacherTreeView teacherView;

    // Only used on the learner side
    private final VirtualLearnerTreeView learnerView;

    // Virtual node path
    private long path;

    // Virtual node hash on the learner side. May be NULL_HASH, if the path is outside of path range
    // in the old learner virtual tree
    private Hash originalHash;

    /**
     * Zero-arg constructor for constructable registry.
     */
    public PullVirtualTreeResponse() {
        teacherView = null;
        learnerView = null;
    }

    /**
     * This constructor is used by the teacher to create new responses.
     */
    public PullVirtualTreeResponse(final VirtualTeacherTreeView teacherView, final PullVirtualTreeRequest request) {
        this.teacherView = teacherView;
        this.learnerView = null;
        this.path = request.getPath();
        this.originalHash = request.getHash();
        assert originalHash != null;
    }

    /**
     * This constructor is used by the learner when deserializing responses.
     *
     * @param learnerTreeView
     * 		the learner's view
     */
    public PullVirtualTreeResponse(final VirtualLearnerTreeView learnerTreeView) {
        this.teacherView = null;
        this.learnerView = learnerTreeView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        assert teacherView != null;
        out.writeLong(path);
        Hash teacherHash = teacherView.loadHash(path);
        // The only valid scenario, when teacherHash may be null, is the empty tree
        if ((teacherHash == null) && (path != 0)) {
            throw new MerkleSerializationException("Cannot load node hash (bad request from learner?), path = " + path);
        }
        final boolean isClean = (teacherHash == null) || teacherHash.equals(originalHash);
        out.write(isClean ? 0 : 1);
        teacherView.writeNode(out, path, isClean);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        assert learnerView != null;
        path = in.readLong();
        final boolean isClean = in.read() == 0;
        learnerView.readNode(in, path, isClean);
    }

    public long getPath() {
        return path;
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
        return ClassVersion.ORIGINAL;
    }
}
