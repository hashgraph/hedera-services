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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
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

    private boolean isClean;

    private long firstLeafPath = -1;
    private long lastLeafPath = -1;

    // If the response is not clean (learner hash != teacher hash), then leafData contains
    // the leaf data on the teacher side
    private VirtualLeafRecord leafData;

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
            final boolean isClean,
            final long firstLeafPath,
            final long lastLeafPath,
            final VirtualLeafRecord leafData) {
        this.teacherView = teacherView;
        this.learnerView = null;
        this.path = path;
        this.isClean = isClean;
        this.firstLeafPath = firstLeafPath;
        this.lastLeafPath = lastLeafPath;
        this.leafData = leafData;
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
        out.write(isClean ? 0 : 1);
        if (path == Path.ROOT_PATH) {
            out.writeLong(firstLeafPath);
            out.writeLong(lastLeafPath);
        }
        if (leafData != null) {
            assert !isClean;
            out.writeSerializable(leafData, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        assert learnerView != null;
        learnerView.getMapStats().incrementTransfersFromTeacher();
        path = in.readLong();
        isClean = in.read() == 0;
        if (path == Path.ROOT_PATH) {
            firstLeafPath = in.readLong();
            lastLeafPath = in.readLong();
            learnerView.setReconnectPaths(firstLeafPath, lastLeafPath);
            if (lastLeafPath <= 0) {
                return;
            }
        }
        final boolean isLeaf = learnerView.isLeaf(path);
        if (isLeaf && !isClean) {
            leafData = in.readSerializable(false, VirtualLeafRecord::new);
        }
        if (learnerView.isLeaf(path)) {
            learnerView.getMapStats().incrementLeafHashes(1, isClean ? 1 : 0);
        } else {
            learnerView.getMapStats().incrementInternalHashes(1, isClean ? 1 : 0);
        }
    }

    public LearnerPullVirtualTreeView getLearnerView() {
        assert learnerView != null;
        return learnerView;
    }

    public long getPath() {
        return path;
    }

    public boolean isClean() {
        return isClean;
    }

    @SuppressWarnings("unchecked")
    public <K extends VirtualKey, V extends VirtualValue> VirtualLeafRecord<K, V> getLeafData() {
        return (VirtualLeafRecord<K, V>) leafData;
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
