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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.internal.Path;
import java.io.IOException;

/**
 * Used during the synchronization protocol to send data needed to reconstruct a single node.
 */
public class PullVirtualTreeRequest implements SelfSerializable {

    private static final long CLASS_ID = 0xecfbef49a90334ffL;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Only used on the teacher side
    private final VirtualTeacherTreeView teacherView;

    // Only used on the learner side
    private final VirtualLearnerTreeView learnerView;

    private long path = -1;

    private Hash hash = null;

    /**
     * Zero-arg constructor for constructable registry.
     */
    public PullVirtualTreeRequest() {
        teacherView = null;
        learnerView = null;
    }

    /**
     * This constructor is used by the teacher to deserialize the request from the stream.
     */
    public PullVirtualTreeRequest(final VirtualTeacherTreeView teacherView) {
        this.teacherView = teacherView;
        this.learnerView = null;
    }

    /**
     * This constructor is used by the learner to send requests to the teacher.
     */
    public PullVirtualTreeRequest(final VirtualLearnerTreeView learnerTreeView, final long path, final Hash hash) {
        this.teacherView = null;
        this.learnerView = learnerTreeView;
        assert path == Path.INVALID_PATH || (path >= 0 && hash != null);
        this.path = path;
        this.hash = hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        assert learnerView != null;
        out.writeLong(path);
        if (hash != null) {
            out.write(hash.getValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        assert teacherView != null;
        path = in.readLong();
        if (path >= 0) {
            hash = new Hash(DigestType.SHA_384);
            if (VirtualReconnectUtils.completelyRead(in, hash.getValue()) != DigestType.SHA_384.digestLength()) {
                throw new IOException("Failed to read node hash from the learner");
            }
        }
    }

    public long getPath() {
        return path;
    }

    public Hash getHash() {
        return hash;
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
