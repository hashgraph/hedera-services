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

import com.hedera.hapi.node.base.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This is a special handler that is used to "finalize" hbar and token transfers for the child transaction record.
 * Finalization in this context means summing the net changes to make to each account's hbar balance and token
 * balances, and assigning the final owner of an nft after an arbitrary number of ownership changes.
 * Based on issue https://github.com/hashgraph/hedera-services/issues/7084 the modularized
 * transaction record for NFT transfer chain A -> B -> C, will look different from mono-service record.
 * This is because mono-service will record both ownership changes from A -> b and then B-> C.
 * NOTE: This record doesn't calculate any staking rewards.
 * Staking rewards are calculated only for parent transaction record.
 * In this finalizer, we will:
 * 1. Iterate through all modifications in writableAccountStore, writableTokenRelationStore.
 * 2. For each modification we look at the same entity's original value
 * 3. Calculate the difference between the two, and then construct a TransferList and TokenTransferList
 * for the child record
 */
public interface ChildRecordFinalizer {
    /**
     * This class is used to "finalize" hbar and token transfers for the child transaction record.
     * It determines the net hbar transfers and token transfers based on the original value from writable state,
     * and based on changes made during this transaction. It then constructs a TransferList and TokenTransferList
     * for the child record.
     * @param context the context
     * @param function the functionality
     */
    void finalizeChildRecord(@NonNull ChildFinalizeContext context, final HederaFunctionality function);
}
