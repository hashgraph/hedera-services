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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.HederaState;
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

    /**
     * Observes the properties and permissions of the network from a saved state.
     */
    public interface SpecialFilesObserver {
        /**
         * Accepts the properties and permissions of the network from a saved state.
         *
         * @param properties the raw bytes of the properties file
         * @param permissions the raw bytes of the permissions file
         */
        void acceptPropertiesAndPermissions(Bytes properties, Bytes permissions);
    }

    /**
     * Given a state, configuration, and observer, observes the properties and
     * permissions of the network.
     *
     * @param state the state to observe
     * @param config the configuration to use
     * @param observer the observer to notify
     */
    public static void observePropertiesAndPermissions(
            @NonNull final HederaState state,
            @NonNull final Configuration config,
            @NonNull final SpecialFilesObserver observer) {
        requireNonNull(state);
        requireNonNull(config);
        requireNonNull(observer);
        final var filesConfig = config.getConfigData(FilesConfig.class);
        observePropertiesAndPermissions(
                state,
                FileUtilities.createFileID(filesConfig.networkProperties(), config),
                FileUtilities.createFileID(filesConfig.hapiPermissions(), config),
                observer);
    }

    private static void observePropertiesAndPermissions(
            @NonNull final HederaState state,
            @NonNull final FileID propertiesId,
            @NonNull final FileID permissionsId,
            @NonNull final SpecialFilesObserver observer) {
        observer.acceptPropertiesAndPermissions(
                getFileContent(state, propertiesId), getFileContent(state, permissionsId));
    }

    public static FileID createFileID(final long fileNum, @NonNull final Configuration configuration) {
        final var hederaConfig = configuration.getConfigData(HederaConfig.class);
        return FileID.newBuilder()
                .realmNum(hederaConfig.realm())
                .shardNum(hederaConfig.shard())
                .fileNum(fileNum)
                .build();
    }
}
