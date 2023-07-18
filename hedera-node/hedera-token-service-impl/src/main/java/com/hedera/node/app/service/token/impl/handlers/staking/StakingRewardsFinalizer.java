/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.staking;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.utils.RewardCalculator;
import com.hedera.pbj.runtime.OneOf;

import edu.umd.cs.findbugs.annotations.NonNull;

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT;

public class StakingRewardsFinalizer {
    private final RewardCalculator rewardCalculator;
    public StakingRewardsFinalizer(@NonNull final RewardCalculator rewardCalculator) {
        this.rewardCalculator = rewardCalculator;
    }

    /**
     * If there is an account X that staked to account Y. If account Y is staked to a node, then
     * change in X balance will contribute to Y's stakedToMe balance. This function will update
     * Y's stakedToMe balance.
     * @param writableAccountStore The store to write to for updated values
     * @param readableAccountStore The store to read from for original values
     */
    public void applyStakedToMeUpdates(final WritableAccountStore writableAccountStore,
                                       final ReadableAccountStore readableAccountStore) {
        final var modifiedAccounts = writableAccountStore.modifiedAccountsInState();
        for (final var id : modifiedAccounts) {
            final var originalAccount = readableAccountStore.getAccountById(id);
            final var modifiedAccount = writableAccountStore.get(id);
            OneOf<Account.StakedIdOneOfType> originalStakedId = null;
            if(originalAccount != null){
                originalStakedId = originalAccount.stakedId();
            }
            final var modifiedStakedId = modifiedAccount.stakedId();
            if(modifiedStakedId.equals(originalStakedId)){
                continue;
            }
            final var scenario = StakeChangeScenario.forCase(originalAccount.stakedId().kind(), modifiedAccount.stakedId().kind());

            // If the stakedId is changed from account to account. Then we need to update the
            // stakedToMe balance of new account
            if (scenario.equals(FROM_ACCOUNT_TO_ACCOUNT) && 
                    originalAccount.stakedAccountId().equals(modifiedAccount.stakedAccountId())) {
                final var roundedFinalBalance = roundedToHbar(modifiedAccount.tinybarBalance());
                final var roundedInitialBalance = roundedToHbar(originalAccount.tinybarBalance());
                final var delta = roundedFinalBalance - roundedInitialBalance;
                // Even if the stakee's total stake hasn't changed, we still want to
                // trigger a reward situation whenever the staker balance changes;
                if(roundedFinalBalance != roundedInitialBalance) {
                    updateStakedToMeFor(modifiedAccount.stakedAccountId(), delta, writableAccountStore);
                }
            } else {
                if (scenario.withdrawsFromAccount()) {
                    final var roundedInitialBalance = roundedToHbar(originalAccount.tinybarBalance());
                    // Always trigger a reward situation for the old stakee when they are
                    // losing an indirect staker, even if it doesn't change their total stake
                    updateStakedToMeFor(originalAccount.stakedAccountId(), -roundedInitialBalance, writableAccountStore);
                }
                if (scenario.awardsToAccount()) {
                    // Always trigger a reward situation for the new stakee when they are
                    // losing an indirect staker, even if it doesn't change their total stake
                    final var roundedFinalBalance = roundedToHbar(originalAccount.tinybarBalance());
                    updateStakedToMeFor(modifiedAccount.stakedAccountId(), roundedFinalBalance, writableAccountStore);
                }
            }
        }
    }

    private void updateStakedToMeFor(final AccountID stakee,
                                     final long roundedFinalBalance,
                                     final WritableAccountStore writableAccountStore) {
        final var account = writableAccountStore.get(stakee);
        final var initialStakedToMe = account.stakedToMe();
        final var copy = account.copyBuilder()
                .stakedToMe(initialStakedToMe + roundedFinalBalance)
                .build();
        writableAccountStore.put(copy);

    }

    private long roundedToHbar(final long value) {
        return (value / HBARS_TO_TINYBARS) * HBARS_TO_TINYBARS;
    }
}
