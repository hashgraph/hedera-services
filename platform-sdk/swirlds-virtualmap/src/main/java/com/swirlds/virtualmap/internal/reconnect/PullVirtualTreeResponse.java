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
@SuppressWarnings("rawtypes")
public class PullVirtualTreeResponse implements SelfSerializable {

    private static final long CLASS_ID = 0xecfbef49a90334e3L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Only used on the teacher side
    private final TeacherPullVirtualTreeView teacherView;

    // Only used on the learner side
    private final LearnerPullVirtualTreeView learnerView;

    // Virtual node path
    private long path;

    // Virtual node hash on the learner side. May be NULL_HASH, if the path is outside of path range
    // in the old learner virtual tree
    private Hash learnerHash;

    private Hash teacherHash;

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
    public PullVirtualTreeResponse(
            final TeacherPullVirtualTreeView teacherView,
            final long path,
            final Hash learnerHash,
            final Hash teacherHash) {
        this.teacherView = teacherView;
        this.learnerView = null;
        this.path = path;
        this.learnerHash = learnerHash;
        assert learnerHash != null;
        this.teacherHash = teacherHash;
        // teacherHash may be null (in case the tree is empty)
    }

    /**
     * This constructor is used by the learner when deserializing responses.
     *
     * @param learnerTreeView
     * 		the learner's view
     */
    public PullVirtualTreeResponse(final LearnerPullVirtualTreeView learnerTreeView) {
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
        final boolean isClean = (teacherHash == null) || teacherHash.equals(learnerHash);
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
        if (learnerView.isLeaf(path)) {
            learnerView.getMapStats().incrementLeafHashes(1, isClean ? 1 : 0);
        } else {
            learnerView.getMapStats().incrementInternalHashes(1, isClean ? 1 : 0);
        }
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
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }
}
