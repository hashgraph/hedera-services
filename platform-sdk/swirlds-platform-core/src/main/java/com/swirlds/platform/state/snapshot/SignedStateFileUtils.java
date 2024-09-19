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

package com.swirlds.platform.state.snapshot;

import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.platform.state.MerkleRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
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

    public static final String SIGNATURE_SET_FILE_NAME = "signatureSet.bin";

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
     * A helper function to read state snapshots, assuming the merkle tree was serialized using
     * {@link com.swirlds.common.io.SelfSerializable} mechanism. Used in {@link
     * com.swirlds.platform.builder.PlatformBuilder}
     */
    @NonNull
    public static MerkleRoot readState(@NonNull final MerkleDataInputStream in, @NonNull final Path dir)
            throws IOException {
        return in.readMerkleTree(dir, MAX_MERKLE_NODES_IN_STATE);
    }
}
