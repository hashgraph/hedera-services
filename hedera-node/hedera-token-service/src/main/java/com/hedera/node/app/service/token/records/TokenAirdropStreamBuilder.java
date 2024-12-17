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

package com.hedera.node.app.service.token.records;

import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} specialization for tracking the effects of a {@code TokenAirdrops}
 * transaction.
 */
public interface TokenAirdropStreamBuilder extends CryptoTransferStreamBuilder {
    /**
     * Adds to the pending airdrop record list.
     *
     * @param pendingAirdropRecord pending airdrop record
     * @return the builder
     */
    TokenAirdropStreamBuilder addPendingAirdrop(@NonNull PendingAirdropRecord pendingAirdropRecord);
}
