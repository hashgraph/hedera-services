package com.hedera.services.ledger;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.expiry.MonotonicFullQueueExpiries;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks changes to ledger entities and aliases over a trailing window of length given by the global/dynamic
 * property <tt>ledger.changeHistorian.memorySecs</tt>. (The change historian will return {@code UNKNOWN} if asked
 * for the change status of an entity outside its tracked window.)
 *
 * For an account, contract, file, topic, or token a <i>change</i> can be a creation, update, deletion, or auto-removal.
 * For a schedule, a <i>change</i> can be creation, deletion, or auto-removal. And for an alias, a <i>change</i> can only
 * be a creation or auto-removal.
 *
 * We need to track changes to safely re-use in {@code handleTransaction} the signatures created during
 * {@code expandSignatures}. The signatures are created in {@code expandSignatures} by looking up keys from a signed
 * state. If during {@code handleTransaction}, none of the entities linked to the transaction have changed since the
 * state was signed, then in particular none of the keys have changed; and we can re-use the expanded signatures
 * (including their asynchronously computed verification status).
 *
 * But if any of the entities <b>have</b> changed, it is possible their keys changed; and we must re-expand the
 * signatures in {@code handleTransaction} to be sure we are up-to-date.
 */
@Singleton
public class ChangeHistorian {
	private final GlobalDynamicProperties dynamicProperties;

	private Instant now;
	private Instant firstNow;
	private boolean fullWindowElapsed = false;
	private final Map<Long, Instant> entityChangeTimes = new HashMap<>();
	private final Map<ByteString, Instant> aliasChangeTimes = new HashMap<>();
	private final MonotonicFullQueueExpiries<Long> entityChangeExpiries = new MonotonicFullQueueExpiries<>();
	private final MonotonicFullQueueExpiries<ByteString> aliasChangeExpiries = new MonotonicFullQueueExpiries<>();

	public enum ChangeStatus {
		CHANGED, UNCHANGED, UNKNOWN
	}

	@Inject
	public ChangeHistorian(final GlobalDynamicProperties dynamicProperties) {
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * Marks the new consensus time at which changes may happen.
	 *
	 * @param now the new consensus time of any changes
	 */
	public void advanceClockTo(final Instant now) {
		this.now = now;

		if (!fullWindowElapsed) {
			manageFirstWindow(now);
		}
	}

	/**
	 * Returns the change status of an entity (by its number) since a given instant.
	 * <ol>
	 *     <li>If the instant is outside this historian's tracking window, returns {@code UNKNOWN}.</li>
	 *     <li>If the entity has a tracked change at or after the given instant, returns {@code CHANGED}.</li>
	 *     <li>If the entity has no tracked changes since the given instant, returns {@code UNCHANGED}.</li>
	 * </ol>
	 *
	 * @param then the time after which changes matter
	 * @param entityNum the number of the entity that may have changed
	 * @return the status of changes to the entity since the given time
	 */
	public ChangeStatus entityStatusSince(final Instant then, final long entityNum) {
		throw new AssertionError("Not implemented");
	}

	/**
	 * Returns the change status of an alias since a given instant.
	 * <ol>
	 *     <li>If the instant is outside this historian's tracking window, returns {@code UNKNOWN}.</li>
	 *     <li>If the alias has a tracked change at or after the given instant, returns {@code CHANGED}.</li>
	 *     <li>If the alias has no tracked changes since the given instant, returns {@code UNCHANGED}.</li>
	 * </ol>
	 *
	 * @param then the time after which changes matter
	 * @param alias the alias that may have changed
	 * @return the status of changes to the alias since the given time
	 */
	public ChangeStatus aliasStatusSince(final Instant then, final ByteString alias) {
		throw new AssertionError("Not implemented");
	}

	/**
	 * Tracks the given entity (by number) as changed at the current time.
	 *
	 * @param entityNum the changed entity
	 */
	public void markEntityChanged(final long entityNum) {
		/* No-op */
	}

	/**
	 * Tracks the given alias as changed at the current time.
	 *
	 * @param alias the changed alias
	 */
	public void markAliasChanged(final ByteString alias) {
		throw new AssertionError("Not implemented");
	}

	private void manageFirstWindow(final Instant now) {
		if (firstNow == null) {
			firstNow = now;
		} else {
			final var elapsedSecs = (int) (now.getEpochSecond() - firstNow.getEpochSecond());
			fullWindowElapsed = elapsedSecs > dynamicProperties.changeHistorianMemorySecs();
			if (fullWindowElapsed) {
				firstNow = null;
			}
		}
	}

	/* --- Only used for unit tests --- */
	Instant getNow() {
		return now;
	}

	Instant getFirstNow() {
		return firstNow;
	}

	boolean isFullWindowElapsed() {
		return fullWindowElapsed;
	}
}
