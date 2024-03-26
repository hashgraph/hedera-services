/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.codec;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.InvalidKeyException;

/**
 * The class is used to convert a {@link com.hedera.node.app.service.mono.files.HFileMeta} content and metadata  to a {@link File} and vice versa during the migration process
 */
public class FileServiceStateTranslator {

    /**
     * The method converts a {@link com.hedera.node.app.service.mono.files.HederaFs} content and metadata  to a {@link File}
     * @param fileID old protobuf fileID that will be used to search in old state
     * @param hederaFs old state
     * @return new protobuf File
     */
    @NonNull
    public static File stateToPbj(
            @NonNull com.hederahashgraph.api.proto.java.FileID fileID,
            @NonNull com.hedera.node.app.service.mono.files.HederaFs hederaFs) {
        requireNonNull(fileID);
        return stateToPbj(hederaFs.cat(fileID), hederaFs.getattr(fileID), fileID);
    }

    /**
     * The method converts a {@link com.hedera.node.app.service.mono.files.HFileMeta} content and metadata  to a {@link File}
     * @param data content of the file in old state
     * @param metadata metadata of the file in old state
     * @param fileID old protobuf fileID that will be used in order to initialize the new protobuf file
     * @return new protobuf File
     */
    @NonNull
    public static File stateToPbj(
            @Nullable final byte[] data,
            @NonNull final com.hedera.node.app.service.mono.files.HFileMeta metadata,
            @NonNull final com.hederahashgraph.api.proto.java.FileID fileID) {
        requireNonNull(metadata);
        requireNonNull(fileID);
        final var fileBuilder = File.newBuilder();
        fileBuilder.fileId(new FileID.Builder()
                .fileNum(fileID.getFileNum())
                .realmNum(fileID.getRealmNum())
                .shardNum(fileID.getShardNum()));
        fileBuilder.expirationSecond(metadata.getExpiry());
        if (metadata.getWacl() != null && !metadata.getWacl().isEmpty()) {
            fileBuilder.keys(PbjConverter.asPbjKey(metadata.getWacl()).keyList());
        }
        if (data != null) {
            fileBuilder.contents(Bytes.wrap(data));
        }
        fileBuilder.memo(metadata.getMemo());
        fileBuilder.deleted(metadata.isDeleted());

        return fileBuilder.build();
    }

    /**
     * The method converts a {@link File} to a {@link com.hedera.node.app.service.mono.files.HFileMeta} content and metadata
     * @param fileID new protobuf fileID that will be used to search in file store
     * @param readableFileStore file store that will be used to search for the file
     * @return File and metadata pair object
     */
    @NonNull
    public static FileMetadataAndContent pbjToState(
            @NonNull FileID fileID, @NonNull ReadableFileStoreImpl readableFileStore) throws InvalidKeyException {
        requireNonNull(fileID);
        requireNonNull(readableFileStore);
        final var optionalFile = readableFileStore.getFileLeaf(fileID);
        if (optionalFile == null) {
            throw new IllegalArgumentException("File not found");
        }
        return pbjToState(optionalFile);
    }

    /**
     * The method converts a {@link File} to a {@link FileMetadataAndContent} content and metadata
     * @param file new protobuf file that will be used
     * @return File and metadata pair object
     */
    @NonNull
    public static FileMetadataAndContent pbjToState(@NonNull File file) throws InvalidKeyException {
        requireNonNull(file);
        var keys = (file.hasKeys())
                ? com.hedera.node.app.service.mono.legacy.core.jproto.JKey.convertKey(
                        Key.newBuilder().keyList(file.keys()).build(), 1)
                : null;
        com.hedera.node.app.service.mono.files.HFileMeta hFileMeta =
                new HFileMeta(file.deleted(), keys, file.expirationSecond(), file.memo());
        final byte[] data = (file.contents() == null) ? null : file.contents().toByteArray();
        return new FileMetadataAndContent(data, hFileMeta);
    }

    @SuppressWarnings("java:S6218")
    public record FileMetadataAndContent(
            @Nullable byte[] data, @NonNull com.hedera.node.app.service.mono.files.HFileMeta metadata) {}
}
