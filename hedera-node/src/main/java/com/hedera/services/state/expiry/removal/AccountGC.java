/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry.removal;

import static com.hedera.services.state.expiry.removal.FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS;
import static com.hedera.services.throttling.MapAccessType.ACCOUNTS_REMOVE;

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Responsible for "garbage collection" of an expired account whose grace period has ended; such an
 * account may still own fungible token units or NFTs, and we need to either,
 *
 * <ol>
 *   <li>Return these assets to the treasuries of their respective token types; or,
 *   <li>"Burn" them, if they belong to a token type that has been deleted or removed.
 * </ol>
 *
 * Doing a treasury return or burn of fungible units is straightforward. NFTs are a problem,
 * however---we do not know <i>which serial numbers</i> the expired account owned. The current
 * implementation responds by simply "stranding" any such NFTs; that is, leaving them in state with
 * an {@code owner} field still set to the now-missing account.
 */
@Singleton
public class AccountGC {
    static final List<MapAccessType> ACCOUNT_REMOVAL_WORK = List.of(ACCOUNTS_REMOVE);
    private final AliasManager aliasManager;
    private final ExpiryThrottle expiryThrottle;
    private final TreasuryReturns treasuryReturns;
    private final SigImpactHistorian sigImpactHistorian;
    private final BackingStore<AccountID, MerkleAccount> backingAccounts;

    @Inject
    public AccountGC(
            final AliasManager aliasManager,
            final ExpiryThrottle expiryThrottle,
            final SigImpactHistorian sigImpactHistorian,
            final TreasuryReturns treasuryReturns,
            final BackingStore<AccountID, MerkleAccount> backingAccounts) {
        this.aliasManager = aliasManager;
        this.expiryThrottle = expiryThrottle;
        this.backingAccounts = backingAccounts;
        this.sigImpactHistorian = sigImpactHistorian;
        this.treasuryReturns = treasuryReturns;
    }

    public CryptoGcOutcome expireBestEffort(
            final EntityNum num, final MerkleAccount expiredAccount) {
        final var nftReturns = treasuryReturns.returnNftsFrom(expiredAccount);
        if (nftReturns.finished()) {
            final var unitReturns = treasuryReturns.returnFungibleUnitsFrom(expiredAccount);
            if (unitReturns.finished() && expiryThrottle.allow(ACCOUNT_REMOVAL_WORK)) {
                completeRemoval(num, expiredAccount);
                return new CryptoGcOutcome(unitReturns, nftReturns, true);
            } else {
                return new CryptoGcOutcome(unitReturns, nftReturns, false);
            }
        } else {
            return new CryptoGcOutcome(UNFINISHED_NOOP_FUNGIBLE_RETURNS, nftReturns, false);
        }
    }

    private void completeRemoval(final EntityNum num, final MerkleAccount expiredAccount) {
        backingAccounts.remove(num.toGrpcAccountId());
        sigImpactHistorian.markEntityChanged(num.longValue());
        if (aliasManager.forgetAlias(expiredAccount.getAlias())) {
            aliasManager.forgetEvmAddress(expiredAccount.getAlias());
            sigImpactHistorian.markAliasChanged(expiredAccount.getAlias());
        }
    }
}
