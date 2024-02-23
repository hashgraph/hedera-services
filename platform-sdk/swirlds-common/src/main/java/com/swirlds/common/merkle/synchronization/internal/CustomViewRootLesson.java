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

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * This lesson describes a root of a subtree that requires a custom view to be used for reconnect.
 */
public class CustomViewRootLesson implements SelfSerializable {

    private static final long CLASS_ID = 0x1defd7fb7e8c303fL;

    private long rootClassId;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * Zero arg constructor for constructable registry.
     */
    public CustomViewRootLesson() {}

    /**
     * Create a new custom view root lesson.
     *
     * @param rootClassId
     * 		the class ID of the node at teh root of the subtree
     */
    public CustomViewRootLesson(final long rootClassId) {
        this.rootClassId = rootClassId;
    }

    /**
     * Get the class ID of the node at the root of the subtree.
     */
    public long getRootClassId() {
        return rootClassId;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(rootClassId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        rootClassId = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
