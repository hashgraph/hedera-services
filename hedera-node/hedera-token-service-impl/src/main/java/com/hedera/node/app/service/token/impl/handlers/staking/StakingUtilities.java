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

package com.hedera.node.app.service.token.impl.handlers.staking;

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.SENTINEL_NODE_ID;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class StakingUtilities {
    private StakingUtilities() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static final long NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE = -1;
    public static final long NO_STAKE_PERIOD_START = -1;

    public static long roundedToHbar(final long value) {
        return (value / HBARS_TO_TINYBARS) * HBARS_TO_TINYBARS;
    }

    public static long totalStake(@NonNull Account account) {
        return account.tinybarBalance() + account.stakedToMe();
    }

    /**
     * Checks if the account has any changes that modified the staking metadata, which is stakedId or declineReward field.
     * @param originalAccount the original account before the transaction
     * @param modifiedAccount the modified account after the transaction
     * @return true if the account has any changes that modified the staking metadata, false otherwise
     */
    public static boolean hasStakeMetaChanges(
            @Nullable final Account originalAccount, @NonNull final Account modifiedAccount) {
        if (originalAccount == null) {
            return modifiedAccount.hasStakedNodeId()
                    || modifiedAccount.hasStakedAccountId()
                    || modifiedAccount.declineReward();
        }
        final var differDeclineReward = originalAccount.declineReward() != modifiedAccount.declineReward();
        final var differStakedNodeId = !originalAccount
                .stakedNodeIdOrElse(SENTINEL_NODE_ID)
                .equals(modifiedAccount.stakedNodeIdOrElse(SENTINEL_NODE_ID));
        final var differStakeAccountId = !originalAccount
                .stakedAccountIdOrElse(AccountID.DEFAULT)
                .equals(modifiedAccount.stakedAccountIdOrElse(AccountID.DEFAULT));
        return differDeclineReward || differStakedNodeId || differStakeAccountId;
    }
}
