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

package com.swirlds.merkle.map.internal;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.Labeled;
import java.io.IOException;

/**
 * Contains information about a {@link com.swirlds.merkle.map.MerkleMap MerkleMap}.
 */
public class MerkleMapInfo extends PartialMerkleLeaf implements Labeled, MerkleLeaf {

    private static final long CLASS_ID = 0x960210640cdcb44fL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private String label = "";

    /**
     * Zero-arg constructor.
     */
    public MerkleMapInfo() {}

    /**
     * Copy constructor.
     */
    private MerkleMapInfo(final MerkleMapInfo that) {
        this.label = that.label;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        label = in.readNormalisedString(MAX_LABEL_LENGTH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleMapInfo copy() {
        return new MerkleMapInfo(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * Set the label.
     */
    public void setLabel(final String label) {
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Label " + label + " exceeds maximum length of " + MAX_LABEL_LENGTH);
        }
        this.label = label;
    }
}
