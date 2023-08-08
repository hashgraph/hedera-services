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

package com.hedera.node.app.spi.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ListCodec<T> implements Codec<List<T>> {
    private final Codec<T> itemCodec;

    private ListCodec(@NonNull final Codec<T> itemCodec) {
        this.itemCodec = itemCodec;
    }

    @NonNull
    public static final <T> ListCodec<T> forItems(@NonNull final Codec<T> itemCodec) {
        return new ListCodec<>(itemCodec);
    }

    @NonNull
    @Override
    public List<T> parse(@NonNull final ReadableSequentialData input) throws IOException {
        final int listLength = input.readInt();
        final List<T> itemList = new ArrayList<>(listLength);
        for (int i = 0; i < listLength; i++) {
            itemList.add(itemCodec.parse(input));
        }
        return itemList;
    }

    @NonNull
    @Override
    public List<T> parseStrict(@NonNull final ReadableSequentialData input) throws IOException {
        return parse(input);
    }

    @Override
    public void write(@NonNull final List<T> itemList, @NonNull final WritableSequentialData output)
            throws IOException {
        final int listLength = itemList.size();
        output.writeInt(listLength);
        for (final T item : itemList) {
            itemCodec.write(item, output);
        }
    }

    @Override
    public int measure(@NonNull final ReadableSequentialData input) throws IOException {
        int entrySize = Integer.BYTES; // start with length
        final int listLength = input.readInt();
        for (int i = 0; i < listLength; i++) {
            entrySize += itemCodec.measure(input);
        }
        return entrySize;
    }

    @Override
    public int measureRecord(final List<T> itemList) {
        int entrySize = Integer.BYTES; // start with length
        for (final T item : itemList) {
            entrySize += itemCodec.measureRecord(item);
        }
        return entrySize;
    }

    @Override
    public boolean fastEquals(@NonNull final List<T> itemList, @NonNull final ReadableSequentialData input) {
        final int listLength = input.readInt();
        if (listLength == itemList.size()) {
            for (final T item : itemList) {
                try {
                    if (!itemCodec.fastEquals(item, input)) return false;
                } catch (IOException ignored) {
                    return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
