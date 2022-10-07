/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import static com.hedera.services.state.serdes.IoUtils.byteStream;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.legacy.core.jproto.JObjectType;
import com.hedera.services.state.serdes.IoUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class HFileMetaSerde {
    public static final int MAX_CONCEIVABLE_MEMO_UTF8_BYTES = 1_024;
    public static final long PRE_MEMO_VERSION = 1;
    public static final long MEMO_VERSION = 2;

    public static byte[] serialize(HFileMeta meta) throws IOException {
        return byteStream(
                out -> {
                    final var serOut = new SerializableDataOutputStream(out);
                    serOut.writeLong(MEMO_VERSION);
                    serOut.writeBoolean(meta.isDeleted());
                    serOut.writeLong(meta.getExpiry());
                    serOut.writeNormalisedString(meta.getMemo());
                    writeNullable(meta.getWacl(), serOut, IoUtils::serializeKey);
                });
    }

    public static HFileMeta deserialize(DataInputStream in) throws IOException {
        long version = in.readLong();
        if (version == PRE_MEMO_VERSION) {
            return readPreMemoMeta(in);
        } else {
            return readMemoMeta(in);
        }
    }

    static HFileMeta readPreMemoMeta(DataInputStream in) throws IOException {
        long objectType = in.readLong();
        if (objectType != JObjectType.FC_FILE_INFO.longValue()) {
            throw new IllegalStateException(
                    String.format("Read illegal object type '%d'!", objectType));
        }
        // Unused legacy length information.
        in.readLong();
        return unpack(in);
    }

    private static HFileMeta readMemoMeta(DataInputStream in) throws IOException {
        final var serIn = new SerializableDataInputStream(in);
        final var isDeleted = serIn.readBoolean();
        final var expiry = serIn.readLong();
        final var memo = serIn.readNormalisedString(MAX_CONCEIVABLE_MEMO_UTF8_BYTES);
        final JKey wacl = readNullable(serIn, JKeySerializer::deserialize);
        return new HFileMeta(isDeleted, wacl, expiry, memo);
    }

    private static HFileMeta unpack(DataInputStream stream) throws IOException {
        boolean deleted = stream.readBoolean();
        long expirationTime = stream.readLong();
        byte[] key = stream.readAllBytes();
        JKey wacl =
                JKeySerializer.deserialize(
                        new SerializableDataInputStream(new ByteArrayInputStream(key)));
        return new HFileMeta(deleted, wacl, expirationTime);
    }

    private HFileMetaSerde() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
