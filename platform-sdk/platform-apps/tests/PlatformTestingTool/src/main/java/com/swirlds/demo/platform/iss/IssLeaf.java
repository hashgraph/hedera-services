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

package com.swirlds.demo.platform.iss;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.util.Random;

public class IssLeaf extends PartialMerkleLeaf implements MerkleLeaf {
    public static final long CLASS_ID = 0xb25a18b65db5a6ccL;
    public static final int CLASS_VERSION = 1;

    /** whether to write random data or not */
    private boolean writeRandom = false;

    public IssLeaf() {}

    public IssLeaf(IssLeaf source) {
        this.writeRandom = source.writeRandom;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        in.readInt();
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        if (writeRandom) {
            out.writeInt(new Random().nextInt());
        } else {
            out.writeInt(0);
        }
    }

    @Override
    public IssLeaf copy() {
        return new IssLeaf(this);
    }

    public void setWriteRandom(boolean writeRandom) {
        this.writeRandom = writeRandom;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return CLASS_VERSION;
    }
}
