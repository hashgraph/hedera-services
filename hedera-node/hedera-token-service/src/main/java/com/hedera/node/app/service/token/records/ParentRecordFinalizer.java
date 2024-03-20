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

import static java.util.Collections.emptySet;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * This class is used to "finalize" hbar and token transfers for the parent transaction record.
 * Finalization in this context means summing the net changes to make to each account's hbar balance and token
 * balances, and assigning the final owner of an nft after an arbitrary number of ownership changes.
 * Based on issue https://github.com/hashgraph/hedera-services/issues/7084 the modularized
 * transaction record for NFT transfer chain A -> B -> C, will look different from mono-service record.
 * This is because mono-service will record both ownership changes from A -> b and then B-> C.
 * Parent record will record any staking rewards paid out due to transaction changes to state.
 * It will deduct any transfer changes that are listed in child transaction records in the parent record.
 *
 * In this finalizer, we will:
 * 1.If staking is enabled, iterate through all modifications in writableAccountStore and compare with the corresponding entity in readableAccountStore
 * 2. Comparing the changes, we look for balance/declineReward/stakedToMe/stakedId fields have been modified,
 * if an account is staking to a node. Construct a list of possibleRewardReceivers
 * 3. Pay staking rewards to any account who has pending rewards
 * 4. Now again, iterate through all modifications in writableAccountStore, writableTokenRelationStore.
 * 5. For each modification we look at the same entity in the respective readableStore
 * 6. Calculate the difference between the two, and then construct a TransferList and TokenTransferList
 * for the parent record (excluding changes from child transaction records)
 */
public interface ParentRecordFinalizer {
    default void finalizeParentRecord(
            @NonNull AccountID payer,
            @NonNull FinalizeContext context,
            HederaFunctionality functionality,
            @NonNull Set<AccountID> explicitRewardReceivers) {
        finalizeParentRecord(payer, context, functionality, explicitRewardReceivers, emptySet());
    }

    void finalizeParentRecord(
            @NonNull AccountID payer,
            @NonNull FinalizeContext context,
            HederaFunctionality functionality,
            @NonNull Set<AccountID> explicitRewardReceivers,
            @NonNull Set<AccountID> prePaidRewardReceivers);
}
