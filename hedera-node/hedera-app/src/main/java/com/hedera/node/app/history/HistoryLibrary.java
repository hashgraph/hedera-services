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

package com.hedera.node.app.history;

import com.hedera.hapi.node.state.history.HistoryAddressBook;
import com.hedera.node.app.history.impl.SchnorrKeyPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * The cryptographic operations required by the {@link HistoryService}.
 */
public interface HistoryLibrary {
    /**
     * Generates a new Schnorr key pair.
     * @return the key pair
     */
    SchnorrKeyPair newSchnorrKeyPair();

    /**
     * Signs the given history with the given Schnorr private key.
     * @param history the message
     * @param privateKey the Schnorr private key
     * @return the signature
     */
    Bytes signHistory(@NonNull Bytes history, @NonNull Bytes privateKey);

    /**
     * Validates the Schnorr signature for the given message and public key.
     *
     * @param publicKey the Schnorr public key
     * @param history the history
     * @param signature the signature
     * @return true if the signature is valid; false otherwise
     */
    boolean verifyHistorySignature(@NonNull Bytes publicKey, @NonNull Bytes history, @NonNull Bytes signature);

    /**
     * Hashes the given address book.
     * @param addressBook the address book
     * @return the hash of the address book
     */
    Bytes hashAddressBook(@NonNull HistoryAddressBook addressBook);

    /**
     * Returns a SNARK recursively proving the target address book and associated metadata belong to the given ledger
     * id's chain of trust that includes the given source address book, based on its own proof of belonging. (Unless the
     * source address book hash <i>is</i> the ledger id, which is the base case of the recursion).
     *
     * @param ledgerId the ledger id
     * @param sourceProof if not null, the proof the source address book is in the ledger id's chain of trust
     * @param sourceAddressBook the source roster
     * @param sourceSignatures the source address book signatures on the target address book hash and its metadata
     * @param targetAddressBookHash the hash of the target address book
     * @param targetMetadata the metadata of the target address book
     * @return the SNARK proving the target address book and metadata belong to the ledger id's chain of trust
     */
    @NonNull
    Bytes proveChainOfTrust(
            @NonNull Bytes ledgerId,
            @Nullable Bytes sourceProof,
            @NonNull HistoryAddressBook sourceAddressBook,
            @NonNull Map<Long, Bytes> sourceSignatures,
            @NonNull Bytes targetAddressBookHash,
            @NonNull Bytes targetMetadata);

    /**
     * Verifies the given SNARK proves the given address book hash and associated metadata belong to the given
     * ledger id's chain of trust
     * @param ledgerId the ledger id
     * @param addressBookHash the hash of the address book
     * @param metadata the metadata associated to the address book
     * @param proof the SNARK proving the address book hash and metadata belong to the ledger id's chain of trust
     * @return true if the proof is valid; false otherwise
     */
    boolean verifyChainOfTrust(
            @NonNull Bytes ledgerId, @NonNull Bytes addressBookHash, @NonNull Bytes metadata, @NonNull Bytes proof);
}
