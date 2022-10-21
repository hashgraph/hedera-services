package com.hedera.services.store.cache;

import com.hedera.services.state.migration.HederaAccount;

import javax.annotation.Nullable;
import java.time.Instant;

public record TimestampedAccount(HederaAccount account, @Nullable Instant lastPossibleChange) {
}
