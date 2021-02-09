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
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.Pause;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;

/**
 * Adds retry-with-backoff functionality to the {@link DefaultFCMapAccountLookup} by
 * delegating the lookup to its superclass instance up to {@code maxRetries} times,
 * with {@code Pause} invocations that increase by {@code retryWaitIncrementMs} between
 * each failed lookup.
 *
 * @author Nathan Klick
 * @author Michael Tinker
 */
public class RetryingFCMapAccountLookup extends DefaultFCMapAccountLookup {
	private static int DEFAULT_MAX_RETRIES = 10;
	private static int DEFAULT_RETRY_WAIT_INCREMENT_MS = 10;
	private static final Logger log = LogManager.getLogger(RetryingFCMapAccountLookup.class);

	private int maxRetries;
	private int retryWaitIncrementMs;
	private final Pause pause;
	private final MiscRunningAvgs runningAvgs;
	private final MiscSpeedometers speedometers;

	private Optional<NodeLocalProperties> properties;

	public RetryingFCMapAccountLookup(
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			int maxRetries,
			int retryWaitIncrementMs,
			Pause pause,
			MiscRunningAvgs runningAvgs,
			MiscSpeedometers speedometers
	) {
		super(accounts);
		this.pause = pause;
		this.properties = Optional.empty();
		this.runningAvgs = runningAvgs;
		this.speedometers = speedometers;
		this.maxRetries = maxRetries;
		this.retryWaitIncrementMs = retryWaitIncrementMs;
	}

	public RetryingFCMapAccountLookup(
			Pause pause,
			NodeLocalProperties properties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			MiscRunningAvgs runningAvgs,
			MiscSpeedometers speedometers
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
	public SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id) {
		maxRetries = properties
				.map(NodeLocalProperties::precheckLookupRetries)
				.orElse(maxRetries);
		retryWaitIncrementMs = properties
				.map(NodeLocalProperties::precheckLookupRetryBackoffMs)
				.orElse(retryWaitIncrementMs);

		final long lookupStart = System.nanoTime();
		int retriesRemaining = maxRetries;

		AccountSigningMetadata meta = superLookup(id);
		if (meta != null) {
			return new SafeLookupResult<>(meta);
		}

		do {
			int retryNo = maxRetries - retriesRemaining + 1;
			if (!pause.forMs(retryNo * retryWaitIncrementMs)) {
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

	private void updateStats(int n, double time) {
		speedometers.cycleAccountLookupRetries();
		runningAvgs.recordAccountLookupRetries(n);
		runningAvgs.recordAccountRetryWaitMs(time);
	}

	private double msElapsedSince(long then) {
		return (System.nanoTime() - (double)then) / 1_000_000L;
	}

	private AccountSigningMetadata superLookup(AccountID id) {
		var result = super.safeLookup(id);
		return result.succeeded() ? result.metadata() : null;
	}
}
