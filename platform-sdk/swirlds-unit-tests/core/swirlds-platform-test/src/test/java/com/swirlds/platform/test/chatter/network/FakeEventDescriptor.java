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

package com.swirlds.platform.test.chatter.network;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.chatter.protocol.messages.EventDescriptor;
import java.io.IOException;

public class FakeEventDescriptor implements EventDescriptor {
    private static final long CLASS_ID = 0x281cc80fd18964f0L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long creator;
    private long order;

    public FakeEventDescriptor(final long creator, final long order) {
        this.creator = creator;
        this.order = order;
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

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

    @Override
    public Hash getHash() {
        return null;
    }

    @Override
    public long getCreator() {
        return creator;
    }

    @Override
    public long getGeneration() {
        return order;
    }

    @Override
    public String toString() {
        return "Desc(" + order + ", c:" + creator + ")";
    }
}
