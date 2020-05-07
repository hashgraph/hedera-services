package com.hedera.services.sigs.metadata.lookups;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.utils.Pause;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Adds retry-with-backoff functionality to the {@link DefaultFCMapAccountLookup} by
 * delegating the lookup to its superclass instance up to {@code maxRetries} times,
 * with {@code Pause} invocations that increase by {@code retryWaitIncrementMs} between
 * each failed lookup.
 *
 * When one or more lookups are attempted, the injected {@link HederaNodeStats} is used
 * to record statistics about the lookups performed.
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
	private Optional<PropertySource> properties;
	final private Pause pause;
	final private HederaNodeStats stats;

	public RetryingFCMapAccountLookup(
			FCMap<MapKey, HederaAccount> accounts,
			int maxRetries,
			int retryWaitIncrementMs,
			Pause pause,
			HederaNodeStats stats
	) {
		super(accounts);
		this.stats = stats;
		this.pause = pause;
		this.properties = Optional.empty();
		this.maxRetries = maxRetries;
		this.retryWaitIncrementMs = retryWaitIncrementMs;
	}

	public RetryingFCMapAccountLookup(
			Pause pause,
			PropertySource properties,
			HederaNodeStats stats,
			FCMap<MapKey, HederaAccount> accounts
	) {
		super(accounts);
		this.stats = stats;
		this.pause = pause;
		this.properties = Optional.of(properties);
		this.maxRetries = DEFAULT_MAX_RETRIES;
		this.retryWaitIncrementMs = DEFAULT_RETRY_WAIT_INCREMENT_MS;
	}

	/**
	 * Returns account signing metadata from the backing {@code FCMap} if
	 * it becomes available within {@code (maxRetries + 1)} attempts.
	 *
	 * @param id the account to recover signing metadata for.
	 * @return the metadata if available.
	 * @throws InvalidAccountIDException if the metadata is never available.
	 */
	@Override
	public AccountSigningMetadata lookup(AccountID id) throws Exception {
		maxRetries = properties
				.map(p -> p.getIntProperty("validation.preConsensus.accountKey.maxLookupRetries"))
				.orElse(maxRetries);
		retryWaitIncrementMs = properties
				.map(p -> p.getIntProperty("validation.preConsensus.accountKey.retryBackoffIncrementMs"))
				.orElse(retryWaitIncrementMs);

		final long lookupStart = System.nanoTime();
		int retriesRemaining = maxRetries;

		AccountSigningMetadata meta = uncheckedLookup(id);
		if (meta != null) { return meta; }

		do {
			int retryNo = maxRetries - retriesRemaining + 1;
			if (!pause.forMs(retryNo * retryWaitIncrementMs)) {
				throw new InvalidAccountIDException("Invalid account!", id);
			}
			meta = uncheckedLookup(id);
			if (meta != null) {
				if (stats != null) {
					stats.lookupRetries(retryNo, msElapsedSince(lookupStart));
				}
				return meta;
			}
			retriesRemaining--;
		} while (retriesRemaining > 0);

		if (stats != null) {
			stats.lookupRetries(maxRetries, msElapsedSince(lookupStart));
		}
		throw new InvalidAccountIDException("Invalid account!", id);
	}

	private double msElapsedSince(long then) {
		return (System.nanoTime() - (double)then) / 1_000_000L;
	}

	private AccountSigningMetadata uncheckedLookup(AccountID id) {
		try {
			return super.lookup(id);
		} catch (Exception ignore) {
			log.warn(ignore.getMessage());
			return null;
		}
	}

}
