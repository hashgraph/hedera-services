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

package com.hedera.node.app.util;

import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.state.HederaState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FileUtilities {

    private FileUtilities() {
        throw new IllegalStateException("Utility class");
    }

    @NonNull
    public static Bytes getFileContent(@NonNull final HederaState state, @NonNull final FileID fileID) {
        final var states = state.getReadableStates(FileService.NAME);
        final var filesMap = states.<FileID, File>get(BLOBS_KEY);
        final var file = filesMap.get(fileID);
        return file != null ? file.contents() : Bytes.EMPTY;
    }
}
