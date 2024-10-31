/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.pipeline;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A bare-bones implementation of {@link VirtualRoot} that doesn't do much of anything.
 */
@ConstructableIgnored
public final class NoOpVirtualRoot extends PartialMerkleLeaf implements VirtualRoot, MerkleLeaf {

    /**
     * Transform this object into an immutable one.
     */
    public void makeImmutable() {
        setImmutable(true);
    }

    @Override
    public long getClassId() {
        return 0;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

    @Override
    public int getClassVersion() {
        return 0;
    }

    @Override
    public NoOpVirtualRoot copy() {
        return null;
    }

    @Override
    public boolean shouldBeFlushed() {
        return false;
    }

    @Override
    public void flush() {}

    @Override
    public boolean isFlushed() {
        return false;
    }

    @Override
    public void waitUntilFlushed() {}

    @Override
    public void merge() {}

    @Override
    public boolean isMerged() {
        return false;
    }

    @Override
    public boolean isHashed() {
        return false;
    }

    @Override
    public void computeHash() {}

    @Override
    public <T> T detach(final Path destination) {
        return null;
    }

    @Override
    public boolean isDetached() {
        return false;
    }

    @Override
    public boolean isRegisteredToPipeline(final VirtualPipeline pipeline) {
        return true;
    }

    @Override
    public void onShutdown(final boolean immediately) {}
}
