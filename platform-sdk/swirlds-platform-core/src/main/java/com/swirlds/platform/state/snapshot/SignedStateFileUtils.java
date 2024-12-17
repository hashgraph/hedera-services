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

import java.util.Set;

/**
 * Utility methods for dealing with signed states on disk.
 */
public final class SignedStateFileUtils {

    public static final String SIGNATURE_SET_FILE_NAME = "signatureSet.bin";

    public static final String HASH_INFO_FILE_NAME = "hashInfo.txt";

    /**
     * The name of the file that contains the human-readable address book in the saved state
     */
    public static final String CURRENT_ADDRESS_BOOK_FILE_NAME = "currentAddressBook.txt";

    /**
     * The initial version of the signature set file
     */
    public static final int INIT_SIG_SET_FILE_VERSION = 1;

    /**
     * The supported versions of the signature set file
     */
    public static final Set<Integer> SUPPORTED_SIGSET_VERSIONS = Set.of(INIT_SIG_SET_FILE_VERSION);

    private SignedStateFileUtils() {}
}
