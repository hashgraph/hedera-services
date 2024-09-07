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

package com.hedera.node.app.tss;

import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiConsumer;

/**
 * The TssBaseService will attempt to generate TSS key material for any set candidate roster, giving it a ledger id and
 * the ability to generate ledger signatures that can be verified by the ledger id.  Once the candidate roster has
 * received its full TSS key material, it can be made available for adoption by the platform.
 * <p>
 * The TssBaseService will also attempt to generate ledger signatures by aggregating share signatures produced by
 * calling {@link #requestLedgerSignature(byte[])}.
 */
public interface TssBaseService extends Service {
    String NAME = "TssBaseService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @Override
    default void registerSchemas(@NonNull final SchemaRegistry registry) {
        // FUTURE - add required schemas
    }

    /**
     * Requests a ledger signature on a message hash.  The ledger signature is computed asynchronously and returned
     * to all consumers that have been registered through {@link #registerLedgerSignatureConsumer}.
     *
     * @param messageHash The hash of the message to be signed by the ledger.
     */
    void requestLedgerSignature(@NonNull byte[] messageHash);

    /**
     * Registers a consumer of the message hash and the ledger signature on the message hash.
     *
     * @param consumer the consumer of ledger signatures and message hashes.
     */
    void registerLedgerSignatureConsumer(@NonNull BiConsumer<byte[], byte[]> consumer);

    /**
     * Unregisters a consumer of the message hash and the ledger signature on the message hash.
     *
     * @param consumer the consumer of ledger signatures and message hashes to unregister.
     */
    void unregisterLedgerSignatureConsumer(@NonNull BiConsumer<byte[], byte[]> consumer);
}
