/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Utility to extract information from byte arrays returned by the {@link HistoryLibrary}, encode protobuf
 * messages in the form the library expects, and so on.
 */
@Singleton
public class HistoryLibraryCodec {
    @Inject
    public HistoryLibraryCodec() {
        // Dagger2
    }

    /**
     * Encodes the given address book hash and metadata into a history record to be signed via
     * {@link HistoryLibrary#signSchnorr(Bytes, Bytes)}.
     * @param addressBookHash an address book hash
     * @param metadata the metadata for the address book
     * @return the bytes for signing
     */
    public @NonNull Bytes encodeHistory(@NonNull final Bytes addressBookHash, @NonNull final Bytes metadata) {
        requireNonNull(addressBookHash);
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Encodes the given roster and public keys into an address book for use with the {@link HistoryLibrary}.
     * @param roster the roster
     * @param publicKeys the available Schnorr public keys for the nodes in the roster
     * @return the history address book
     */
    public @NonNull Bytes encodeAddressBook(@NonNull final Roster roster, @NonNull final Map<Long, Bytes> publicKeys) {
        requireNonNull(roster);
        requireNonNull(publicKeys);
        throw new UnsupportedOperationException("Not implemented");
    }
}
