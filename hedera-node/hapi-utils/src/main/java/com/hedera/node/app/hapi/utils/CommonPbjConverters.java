/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class CommonPbjConverters {
    public static @NonNull com.hederahashgraph.api.proto.java.Query fromPbj(@NonNull Query query) {
        requireNonNull(query);
        try {
            final var bytes = asBytes(Query.PROTOBUF, query);
            return com.hederahashgraph.api.proto.java.Query.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public static com.hederahashgraph.api.proto.java.File fromPbj(@Nullable File file) {
        var builder = com.hederahashgraph.api.proto.java.File.newBuilder();
        if (file != null) {
            builder.setFileId(fromPbj(file.fileIdOrThrow()));
            builder.setExpirationSecond(file.expirationSecond());
            builder.setKeys(pbjToProto(
                    file.keysOrElse(KeyList.DEFAULT), KeyList.class, com.hederahashgraph.api.proto.java.KeyList.class));
            builder.setContents(ByteString.copyFrom(file.contents().toByteArray()));
            builder.setMemo(file.memo());
            builder.setDeleted(file.deleted());
        }
        return builder.build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.TransactionBody fromPbj(@NonNull TransactionBody tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(TransactionBody.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.TransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.Key fromPbj(@NonNull Key keyValue) {
        requireNonNull(keyValue);
        try {
            final var bytes = asBytes(Key.PROTOBUF, keyValue);
            return com.hederahashgraph.api.proto.java.Key.parseFrom(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends Record> byte[] asBytes(@NonNull Codec<T> codec, @NonNull T tx) {
        requireNonNull(codec);
        requireNonNull(tx);
        try {
            final var bytes = new ByteArrayOutputStream();
            codec.write(tx, new WritableStreamingData(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unable to convert from PBJ to bytes", e);
        }
    }

    public static @NonNull byte[] asBytes(@NonNull Bytes b) {
        final var buf = new byte[Math.toIntExact(b.length())];
        b.getBytes(0, buf);
        return buf;
    }

    /**
     * Tries to convert a PBJ {@link ResponseType} to a {@link com.hederahashgraph.api.proto.java.ResponseType}
     * @param responseType the PBJ {@link ResponseType} to convert
     * @return the converted {@link com.hederahashgraph.api.proto.java.ResponseType} if valid
     */
    public static @NonNull com.hederahashgraph.api.proto.java.ResponseType fromPbjResponseType(
            @NonNull final ResponseType responseType) {
        return switch (requireNonNull(responseType)) {
            case ANSWER_ONLY -> com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
            case ANSWER_STATE_PROOF -> com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
            case COST_ANSWER -> com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
            case COST_ANSWER_STATE_PROOF -> com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;
        };
    }

    public static <T extends Record, R extends GeneratedMessageV3> R pbjToProto(
            final T pbj, final Class<T> pbjClass, final Class<R> protoClass) {
        try {
            final var codecField = pbjClass.getDeclaredField("PROTOBUF");
            final var codec = (Codec<T>) codecField.get(null);
            final var bytes = asBytes(codec, pbj);
            final var protocParser = protoClass.getMethod("parseFrom", byte[].class);
            return (R) protocParser.invoke(null, bytes);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            // Should be impossible, so just propagate an exception
            throw new RuntimeException("Invalid conversion to proto for " + pbjClass.getSimpleName(), e);
        }
    }

    public static com.hederahashgraph.api.proto.java.FileID fromPbj(final FileID someFileId) {
        return com.hederahashgraph.api.proto.java.FileID.newBuilder()
                .setRealmNum(someFileId.realmNum())
                .setShardNum(someFileId.shardNum())
                .setFileNum(someFileId.fileNum())
                .build();
    }
}
