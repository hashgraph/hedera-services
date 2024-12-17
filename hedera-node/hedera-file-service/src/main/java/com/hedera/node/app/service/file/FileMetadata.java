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

package com.hedera.node.app.service.file;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * File metadata.
 *
 * @param fileId                   file's ID
 * @param expirationTimestamp      file's expiration time
 * @param keys                     file's sign keys
 * @param contents                 file bytes that are the contents of the file
 * @param memo                     file's memo
 * @param deleted                  file's deleted flag
 */
public record FileMetadata(
        FileID fileId,
        @Nullable Timestamp expirationTimestamp,
        @NonNull KeyList keys,
        @NonNull Bytes contents,
        @Nullable String memo,
        boolean deleted,
        @Nullable Timestamp preSystemDeleteExpirationSecond) {}
