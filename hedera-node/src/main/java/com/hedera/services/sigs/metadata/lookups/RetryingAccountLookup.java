package com.hedera.services.sigs.metadata.lookups;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.Pause;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;

import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;

/**
 * Adds retry-with-backoff functionality to the {@link DefaultAccountLookup} by
 * delegating the lookup to its superclass instance up to {@code maxRetries} times,
 * with {@code Pause} invocations that increase by {@code retryWaitIncrementMs} between
 * each failed lookup.
 */
public class RetryingAccountLookup extends DefaultAccountLookup {
	private static final int DEFAULT_MAX_RETRIES = 10;
	private static final int DEFAULT_RETRY_WAIT_INCREMENT_MS = 10;

	private int maxRetries;
	private int retryWaitIncrementMs;
	private final Pause pause;
	private final MiscRunningAvgs runningAvgs;
	private final MiscSpeedometers speedometers;

	private Optional<NodeLocalProperties> properties;

	public RetryingAccountLookup(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final int maxRetries,
			final int retryWaitIncrementMs,
			final Pause pause,
			final MiscRunningAvgs runningAvgs,
			final MiscSpeedometers speedometers
	) {
		super(accounts);
		this.pause = pause;
		this.properties = Optional.empty();
		this.runningAvgs = runningAvgs;
		this.speedometers = speedometers;
		this.maxRetries = maxRetries;
		this.retryWaitIncrementMs = retryWaitIncrementMs;
	}

	public RetryingAccountLookup(
			final Pause pause,
			final NodeLocalProperties properties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final MiscRunningAvgs runningAvgs,
			final MiscSpeedometers speedometers
	) {
		super(accounts);
		this.pause = pause;
		this.properties = Optional.of(properties);
		this.runningAvgs = runningAvgs;
		this.speedometers = speedometers;
		this.maxRetries = DEFAULT_MAX_RETRIES;
		this.retryWaitIncrementMs = DEFAULT_RETRY_WAIT_INCREMENT_MS;
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> safeLookup(final EntityNum id) {
		maxRetries = properties
				.map(NodeLocalProperties::precheckLookupRetries)
				.orElse(maxRetries);
		retryWaitIncrementMs = properties
				.map(NodeLocalProperties::precheckLookupRetryBackoffMs)
				.orElse(retryWaitIncrementMs);

		final long lookupStart = System.nanoTime();
		int retriesRemaining = maxRetries;

		var meta = superLookup(id);
		if (meta != null) {
			return new SafeLookupResult<>(meta);
		}

		do {
			final int retryNo = maxRetries - retriesRemaining + 1;
			if (!pause.forMs((long) retryNo * retryWaitIncrementMs)) {
				return SafeLookupResult.failure(MISSING_ACCOUNT);
			}
			meta = superLookup(id);
			if (meta != null) {
				if (isInstrumented()) {
					updateStats(retryNo, msElapsedSince(lookupStart));
				}
				return new SafeLookupResult<>(meta);
			}
			retriesRemaining--;
		} while (retriesRemaining > 0);

		if (isInstrumented()) {
			updateStats(maxRetries, msElapsedSince(lookupStart));
		}
		return SafeLookupResult.failure(MISSING_ACCOUNT);
	}

	private boolean isInstrumented() {
		return runningAvgs != null && speedometers != null;
	}

	private void updateStats(final int n, final double time) {
		speedometers.cycleAccountLookupRetries();
		runningAvgs.recordAccountLookupRetries(n);
		runningAvgs.recordAccountRetryWaitMs(time);
	}

	private double msElapsedSince(final long then) {
		return (System.nanoTime() - (double) then) / 1_000_000L;
	}

	private AccountSigningMetadata superLookup(final AccountID id) {
		final var result = super.safeLookup(id);
		return result.succeeded() ? result.metadata() : null;
	}
}
