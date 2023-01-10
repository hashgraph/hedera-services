/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.ledger.interceptors;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_SMART_CONTRACT;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.CommitInterceptor;
import com.hedera.node.app.service.mono.ledger.EntityChangeSet;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.validation.AccountUsageTracking;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * A {@link CommitInterceptor} implementation that tracks the hbar adjustments being committed, and
 * throws {@link IllegalStateException} if the adjustments do not sum to zero.
 *
 * <p>In future work, this interceptor will be extended to capture <i>all</i> externalized
 * side-effects of a transaction on the {@code accounts} ledger, including (for example) approved
 * allowances and new aliases.
 */
public class AccountsCommitInterceptor
        implements CommitInterceptor<AccountID, HederaAccount, AccountProperty> {
    private int numNewAccounts;
    private int numNewContracts;
    @Nullable private final AccountUsageTracking usageTracking;
    private final SideEffectsTracker sideEffectsTracker;

    public AccountsCommitInterceptor(
            final AccountUsageTracking usageTracking, final SideEffectsTracker sideEffectsTracker) {
        this.usageTracking = usageTracking;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker) {
        this.usageTracking = null;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    /**
     * Accepts a pending change set, including creations. Removals are not supported.
     *
     * @throws IllegalStateException if these changes are invalid
     */
    @Override
    public void preview(
            final EntityChangeSet<AccountID, HederaAccount, AccountProperty> pendingChanges) {
        numNewAccounts = 0;
        numNewContracts = 0;
        if (pendingChanges.size() == 0) {
            return;
        }

        for (int i = 0, n = pendingChanges.size(); i < n; i++) {
            final var account = pendingChanges.entity(i);
            final var changes = pendingChanges.changes(i);
            // A null account means a new entity
            if (account == null && usageTracking != null) {
                if (Boolean.TRUE.equals(changes.get(IS_SMART_CONTRACT))) {
                    numNewContracts++;
                } else {
                    numNewAccounts++;
                }
            }
            trackBalanceChangeIfAny(pendingChanges.id(i).getAccountNum(), account, changes);
        }

        assertZeroSum();
    }

    @Override
    public void postCommit() {
        if (usageTracking != null) {
            if (numNewContracts > 0) {
                usageTracking.recordContracts(numNewContracts);
            }
            if (numNewAccounts > 0) {
                usageTracking.refreshAccounts();
            }
        }
    }

    private void trackBalanceChangeIfAny(
            final long accountNum,
            @Nullable final HederaAccount merkleAccount,
            @NonNull final Map<AccountProperty, Object> accountChanges) {
        if (accountChanges.containsKey(AccountProperty.BALANCE)) {
            final long newBalance = (long) accountChanges.get(AccountProperty.BALANCE);
            if (newBalance < 0) {
                throw new IllegalStateException(
                        "Cannot set "
                                + ((merkleAccount == null)
                                        ? "<N/A>"
                                        : merkleAccount.getEntityNum().toIdString())
                                + " balance to "
                                + newBalance);
            }
            final long adjustment =
                    (merkleAccount != null) ? newBalance - merkleAccount.getBalance() : newBalance;
            sideEffectsTracker.trackHbarChange(accountNum, adjustment);
        }
    }

    private void assertZeroSum() {
        if (sideEffectsTracker.getNetHbarChange() != 0) {
            throw new IllegalStateException("Invalid balance changes");
        }
    }
}
