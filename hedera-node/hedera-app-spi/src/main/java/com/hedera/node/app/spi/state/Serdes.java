/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface Serdes<T> {
    /**
     * Parses an object from the {@link DataInput} and returns it.
     *
     * @param input The {@link DataInput} from which to read the data to construct an object
     * @return The parsed object. It must not return null.
     * @throws IOException If it is impossible to read from the {@link DataInput}
     */
    @Nullable
    T parse(@NonNull DataInput input) throws IOException;

    /**
     * Writes an item to the given {@link DataOutput}.
     *
     * @param item The item to write. Must not be null.
     * @param output The {@link DataOutput} to write to.
     * @throws IOException If the {@link DataOutput} cannot be written to.
     */
    void write(@Nullable T item, @NonNull DataOutput output) throws IOException;

    /**
     * Reads from this data input the length of the data within the input. The implementation may
     * read all the data, or just some special serialized data, as needed to find out the length of
     * the data.
     *
     * @param input The input to use
     * @return The length of the data item in the input
     * @throws IOException If it is impossible to read from the {@link DataInput}
     */
    int measure(@NonNull DataInput input) throws IOException;

    int typicalSize();

    boolean fastEquals(@NonNull T item, DataInput input);
}
