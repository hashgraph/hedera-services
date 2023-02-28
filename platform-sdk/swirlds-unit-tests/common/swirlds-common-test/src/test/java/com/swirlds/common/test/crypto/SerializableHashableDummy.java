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

package com.swirlds.common.test.crypto;

import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public class SerializableHashableDummy extends AbstractSerializableHashable {
    private static final long CLASS_ID = 0xeecd8387d5496ba3L;
    private static final int CLASS_VERSION = 1;
    private static final int MAX_STRING_LENGTH = 512;

    private int number;
    private String string;

    // For RuntimeConstructable
    public SerializableHashableDummy() {}

    public SerializableHashableDummy(int number, String string) {
        this.number = number;
        this.string = string;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeInt(number);
        out.writeNormalisedString(string);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        number = in.readInt();
        string = in.readNormalisedString(MAX_STRING_LENGTH);
    }
}
