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

import com.hedera.hapi.node.state.history.HistoryAddressBook;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * Default implementation of the {@link HistoryLibrary}.
 */
public class HistoryLibraryImpl implements HistoryLibrary {
    @Override
    public SchnorrKeyPair newSchnorrKeyPair() {
        return new SchnorrKeyPair(Bytes.EMPTY, Bytes.EMPTY);
    }

    @Override
    public Bytes hashAddressBook(@NonNull final HistoryAddressBook addressBook) {
        requireNonNull(addressBook);
        return Bytes.EMPTY;
    }

    @Override
    public Bytes signHistory(@NonNull final Bytes history, @NonNull final Bytes privateKey) {
        requireNonNull(history);
        requireNonNull(privateKey);
        return Bytes.EMPTY;
    }

    @Override
    public boolean verifyHistorySignature(
            @NonNull final Bytes publicKey, @NonNull final Bytes history, @NonNull final Bytes signature) {
        requireNonNull(publicKey);
        requireNonNull(history);
        requireNonNull(signature);
        return true;
    }

    @NonNull
    @Override
    public Bytes proveChainOfTrust(
            @NonNull Bytes ledgerId,
            @Nullable final Bytes sourceProof,
            @NonNull final HistoryAddressBook sourceAddressBook,
            @NonNull final Map<Long, Bytes> sourceSignatures,
            @NonNull final Bytes targetAddressBookHash,
            @NonNull final Bytes targetMetadata) {
        requireNonNull(ledgerId);
        requireNonNull(sourceAddressBook);
        requireNonNull(targetAddressBookHash);
        requireNonNull(targetMetadata);
        requireNonNull(sourceSignatures);
        return Bytes.EMPTY;
    }

    @Override
    public boolean verifyChainOfTrust(
            @NonNull final Bytes ledgerId,
            @NonNull final Bytes addressBookHash,
            @NonNull final Bytes metadata,
            @NonNull final Bytes proof) {
        requireNonNull(ledgerId);
        requireNonNull(addressBookHash);
        requireNonNull(metadata);
        requireNonNull(proof);
        return false;
    }
}
