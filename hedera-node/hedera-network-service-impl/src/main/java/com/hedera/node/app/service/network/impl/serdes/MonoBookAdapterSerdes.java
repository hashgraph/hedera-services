/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.network.impl.serdes;

import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MonoBookAdapterSerdes implements Serdes<AddressBook> {
    private static final int ADDRESS_BOOK_VERSION = 4;

    @NonNull
    @Override
    public AddressBook parse(final @NonNull DataInput input) throws IOException {
        if (input instanceof SerializableDataInputStream in) {
            final var book = new AddressBook();
            book.deserialize(in, ADDRESS_BOOK_VERSION);
            return book;
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataInputStream");
        }
    }

    @Override
    public void write(final @NonNull AddressBook item, final @NonNull DataOutput output)
            throws IOException {
        if (output instanceof SerializableDataOutputStream out) {
            item.serialize(out);
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
        }
    }

    @Override
    public int measure(@NonNull DataInput input) throws IOException {
        return 0;
    }

    @Override
    public int typicalSize() {
        return 0;
    }

    @Override
    public boolean fastEquals(@NonNull AddressBook item, @NonNull DataInput input) {
        return false;
    }
}
