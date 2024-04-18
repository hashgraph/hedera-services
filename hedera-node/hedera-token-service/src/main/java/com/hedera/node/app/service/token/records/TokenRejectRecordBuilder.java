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

package com.hedera.node.app.service.token.records;

import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A {@code RecordBuilder} specialization for tracking the effects of a {@code TokenReject} transaction.
 */
public interface TokenRejectRecordBuilder extends SingleTransactionRecordBuilder {

    /**
     * Tracks token transfers from the payer account to the treasuries as a result of TokenReject transaction,
     * applicable for both fungible and non-fungible tokens.
     *
     * @param tokenTransferLists A list of {@link TokenTransferList} objects detailing the transfers for each token.
     * @return this builder.
     */
    @NonNull
    TokenRejectRecordBuilder tokenTransferLists(@NonNull List<TokenTransferList> tokenTransferLists);
}
