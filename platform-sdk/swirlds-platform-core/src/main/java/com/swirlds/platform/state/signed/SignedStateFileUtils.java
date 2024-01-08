/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.platform.NodeId;
import java.nio.file.Path;

/**
 * Utility methods for dealing with signed states on disk.
 */
public final class SignedStateFileUtils {
    /**
     * Fun trivia: the file extension ".swh" stands for "SWirlds Hashgraph", although this is a bit misleading... as
     * this file doesn't actually contain a hashgraph.
     */
    public static final String SIGNED_STATE_FILE_NAME = "SignedState.swh";

    public static final String HASH_INFO_FILE_NAME = "hashInfo.txt";

    /**
     * The name of the file that contains the human-readable address book in the saved state
     */
    public static final String CURRENT_ADDRESS_BOOK_FILE_NAME = "currentAddressBook.txt";

    /**
     * The signed state file was not versioned before, this byte was introduced to mark a versioned file
     */
    public static final byte VERSIONED_FILE_BYTE = Byte.MAX_VALUE;

    /**
     * The current version of the signed state file
     */
    public static final int FILE_VERSION = 1;

    public static final int MAX_MERKLE_NODES_IN_STATE = Integer.MAX_VALUE;

    private SignedStateFileUtils() {}

    /**
     * Same as {@link SignedStateFilePath#getSignedStatesBaseDirectory()} but uses the config from
     * {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    public static Path getSignedStatesBaseDirectory() {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(ConfigurationHolder.getConfigData(StateConfig.class))
                .getSignedStatesBaseDirectory();
    }

    /**
     * Same as {@link SignedStateFilePath#getSignedStatesDirectoryForApp(String)} but uses the config from
     * {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    public static Path getSignedStatesDirectoryForApp(final String mainClassName) {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(ConfigurationHolder.getConfigData(StateConfig.class))
                .getSignedStatesDirectoryForApp(mainClassName);
    }

    /**
     * Same as {@link SignedStateFilePath#getSignedStatesDirectoryForNode(String, NodeId)} but uses the config from
     * {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    public static Path getSignedStatesDirectoryForNode(final String mainClassName, final NodeId selfId) {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(ConfigurationHolder.getConfigData(StateConfig.class))
                .getSignedStatesDirectoryForNode(mainClassName, selfId);
    }

    /**
     * Same as {@link SignedStateFilePath#getSignedStatesDirectoryForSwirld(String, NodeId, String)} but uses the config
     * from {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    public static Path getSignedStatesDirectoryForSwirld(
            final String mainClassName, final NodeId selfId, final String swirldName) {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(ConfigurationHolder.getConfigData(StateConfig.class))
                .getSignedStatesDirectoryForSwirld(mainClassName, selfId, swirldName);
    }

    /**
     * Same as {@link SignedStateFilePath#getSignedStateDirectory(String, NodeId, String, long)} but uses the config
     * from {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    public static Path getSignedStateDirectory(
            final String mainClassName, final NodeId selfId, final String swirldName, final long round) {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(ConfigurationHolder.getConfigData(StateConfig.class))
                .getSignedStateDirectory(mainClassName, selfId, swirldName, round);
    }
}
