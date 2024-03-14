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

package com.hedera.node.app.bbm.files;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/** Holds the content and the metadata for a single data file in the store */
@SuppressWarnings("java:S6218") // "Equals/hashcode methods should be overridden in records containing array fields"
// not using this with equals
public record HederaFile(
        @NonNull FileStore fileStore,
        @NonNull Integer fileId,
        @NonNull byte[] contents,
        @Nullable HFileMeta metadata,
        @Nullable SystemFileType systemFileType) {

    static HederaFile fromMod(@NonNull final OnDiskValue<File> wrapper) {
        final var value = wrapper.getValue();
        return new HederaFile(
                FileStore.ORDINARY,
                (int) value.fileId().fileNum(),
                value.contents().toByteArray(),
                null,
                SystemFileType.byId.get((int) value.fileId().fileNum()));
    }

    @NonNull
    static HederaFile of(final int fileId, @NonNull final byte[] contents) {
        return new HederaFile(FileStore.ORDINARY, fileId, contents, null, SystemFileType.byId.get(fileId));
    }

    @NonNull
    static HederaFile of(final int fileId, @NonNull final byte[] contents, @NonNull final HFileMeta metadata) {
        return new HederaFile(FileStore.ORDINARY, fileId, contents, metadata, SystemFileType.byId.get(fileId));
    }

    boolean isActive() {
        if (null != systemFileType) {
            return true;
        }
        if (null != metadata) {
            return !metadata.isDeleted();
        }
        return false;
    }
}
