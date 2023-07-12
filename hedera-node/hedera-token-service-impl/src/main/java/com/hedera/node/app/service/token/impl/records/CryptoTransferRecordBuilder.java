/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.records;

import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A {@code RecordBuilder} specialization for tracking the effects of a {@code CryptoTransfer}
 * transaction.
 */
public interface CryptoTransferRecordBuilder {
    /**
     * Tracks the <b>net</b> hbar transfers that need to be applied to the associated accounts
     * (accounts are specified in the {@code TransferList} input param)
     *
     * @param hbarTransfers the net list of adjustments to make to account balances
     * @return this builder
     */
    @NonNull
    CryptoTransferRecordBuilder transferList(@NonNull TransferList hbarTransfers);

    /**
     * Tracks the <b>net</b> token transfers that need to be applied to the associated accounts,
     * including both fungible and non-fungible types
     *
     * @param tokenTransferLists the net list of balance or ownership changes for the given
     *                           fungible and non-fungible tokens
     * @return this builder
     */
    @NonNull
    CryptoTransferRecordBuilder tokenTransferLists(@NonNull List<TokenTransferList> tokenTransferLists);

    @NonNull
    CryptoTransferRecordBuilder assessedCustomFees(List<AssessedCustomFee> assessedCustomFees);
}
