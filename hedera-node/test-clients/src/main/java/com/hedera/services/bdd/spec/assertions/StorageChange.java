/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.assertions;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;

public class StorageChange {
    private ByteString slot;
    private ByteString valueRead;
    private BytesValue valueWritten;

    private StorageChange(ByteString slot, ByteString value) {
        this.slot = slot;
        this.valueRead = value;
    }

    private StorageChange(ByteString slot, ByteString prevValue, BytesValue value) {
        this.slot = slot;
        this.valueRead = prevValue;
        this.valueWritten = value;
    }

    public static StorageChange onlyRead(ByteString slot, ByteString value) {
        return new StorageChange(slot, value);
    }

    public static StorageChange readAndWritten(ByteString slot, ByteString prevValue, ByteString value) {
        return new StorageChange(slot, prevValue, BytesValue.of(value));
    }

    public ByteString getSlot() {
        return this.slot;
    }

    public ByteString getValueRead() {
        return this.valueRead;
    }

    public BytesValue getValueWritten() {
        return this.valueWritten;
    }
}
