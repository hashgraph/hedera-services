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
 * Used during the synchronization protocol to send data needed to reconstruct a single node.
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

    private long path = -1;

    // Only used on the teacher side
    private Hash hash;

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
        this.hash = request.getHash();
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
        if ((teacherHash == null) && (path != 0)) {
            throw new MerkleSerializationException("Cannot load node hash (bad request from learner?), path = " + path);
        }
        final boolean isClean = (teacherHash == null) || teacherHash.equals(hash);
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
