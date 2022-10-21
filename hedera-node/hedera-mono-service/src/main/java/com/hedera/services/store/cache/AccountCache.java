package com.hedera.services.store.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.services.ServicesState;
import com.hedera.services.ledger.ImpactHistorian;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.hedera.services.ledger.ImpactHistorian.ChangeStatus.UNCHANGED;

@Singleton
public class AccountCache {
    private static final long MAX_CACHED_ACCOUNTS = 100_000L;
    private final AtomicReference<ServicesState> immutableStateRef;
    private final Supplier<AccountStorageAdapter> workingAccounts;
    private final Cache<EntityNum, TimestampedAccount> cache;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final ImpactHistorian impactHistorian;

    @Inject
    public AccountCache(
            @ImmutableState final AtomicReference<ServicesState> ref,
            final Supplier<AccountStorageAdapter> workingAccounts,
            final ConsensusTimeTracker consensusTimeTracker,
            final ImpactHistorian impactHistorian
    ) {
        this.immutableStateRef = ref;
        this.workingAccounts = workingAccounts;
        this.consensusTimeTracker = consensusTimeTracker;
        this.impactHistorian = impactHistorian;
        this.cache = Caffeine.newBuilder()
                        .maximumSize(MAX_CACHED_ACCOUNTS)
                        .softValues()
                        .build();
    }

    public @Nullable HederaAccount getGuaranteedLatestInHandle(final EntityNum num) {
        final var timestampedAccount = cache.getIfPresent(num);
        if (timestampedAccount != null) {
            final var then = timestampedAccount.lastPossibleChange();
            if (impactHistorian.entityStatusSince(then, num.longValue()) == UNCHANGED) {
                return timestampedAccount.account();
            }
        }
        final var account = workingAccounts.get().get(num);
        if (account == null && timestampedAccount != null) {
            cache.invalidate(num);
        } else if (account != null) {
            cache.put(num, new TimestampedAccount(account, consensusTimeTracker.getCurrentTxnTime()));
        }
        return account;
    }

    public void updateNow(final EntityNum num, final HederaAccount readOnlyAccount) {
        cache.put(num, new TimestampedAccount(readOnlyAccount, consensusTimeTracker.getCurrentTxnTime()));
    }

    public void removeNow(final EntityNum num) {
        cache.invalidate(num);
    }

    public @Nullable HederaAccount getIfAvailable(final EntityNum num) {
        final var cached = cache.getIfPresent(num);
        if (cached != null) {
            return cached.account();
        }
        final var immutableState = immutableStateRef.get();
        if (immutableState != null) {
            final var immutable = readThrough(num, immutableState);
            if (immutable != null) {
                return immutable;
            }
        }
        return workingAccounts.get().get(num);
    }

    private @Nullable HederaAccount readThrough(final EntityNum num, final ServicesState state) {
        final var account = state.accounts().get(num);
        if (account != null) {
            cache.put(num, new TimestampedAccount(account, state.getTimeOfLastHandledTxn()));
            return account;
        }
        return null;
    }
}
