/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.state.expiry.backgroundworker.jobs.light;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.state.expiry.ExpiryEvent;
import com.hedera.services.state.expiry.PriorityQueueExpiries;
import com.hedera.services.state.expiry.backgroundworker.jobs.Job;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobEntityClassification;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobStatus;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.utils.MiscUtils.forEach;
import static java.util.Comparator.comparing;

/**
 * Responsible for cleaning up short-lived entities - scheduled txns.
 */
public class ExpiringShortLivedEntities implements Job {

	private static final Comparator<ExpiryEvent<Pair<Long, Consumer<EntityId>>>> PQ_CMP = Comparator
			.comparingLong(ExpiryEvent<Pair<Long, Consumer<EntityId>>>::getExpiry)
			.thenComparingLong(ee -> ee.getId().getKey());
	private final JobEntityClassification classification = JobEntityClassification.LIGHTWEIGHT;
	private final ScheduleStore scheduleStore;
	private final Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules;
	private final PriorityQueueExpiries<Pair<Long, Consumer<EntityId>>> shortLivedEntityExpiries =
			new PriorityQueueExpiries<>(PQ_CMP);
	private long shard;
	private long realm;
	private JobStatus status;

	public ExpiringShortLivedEntities(
			HederaNumbers numbers,
			Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules,
			ScheduleStore scheduleStore
	) {
		this.shard = numbers.shard();
		this.realm = numbers.realm();
		this.scheduleStore = scheduleStore;
		this.schedules = schedules;
	}

	@Override
	public boolean execute(long now) {
		while (shortLivedEntityExpiries.hasExpiringAt(now)) {
			var current = shortLivedEntityExpiries.expireNextAt(now);
			current.getValue().accept(entityWith(current.getKey()));
		}
		return true;
	}

	/**
	 * Begins tracking an expiration event.
	 *
	 * @param event
	 * 		the expiration event to track
	 * @param expiry
	 * 		the earliest consensus second at which it should fire
	 */
	public void trackExpirationEvent(Pair<Long, Consumer<EntityId>> event, long expiry) {
		shortLivedEntityExpiries.track(event, expiry);
	}

	/**
	 * Entities that typically expire on the order of days or months (topics, accounts, tokens, etc.)
	 * are monitored and automatically renewed or removed by the EntityAutoProcessing process.
	 * <p>
	 * The only entities that currently qualify as "short-lived" are schedule entities, which have
	 * a default expiration time of 30 minutes. So this method's only function is to scan the
	 * current {@code schedules} FCM and enqueue their expiration events.
	 */
	@Override
	public void reviewExistingEntities() {
		shortLivedEntityExpiries.reset();

		final var _shortLivedEntityExpiries = new ArrayList<Map.Entry<Pair<Long, Consumer<EntityId>>, Long>>();
		final var currentSchedules = schedules.get();
		forEach(currentSchedules, (id, schedule) -> {
			Consumer<EntityId> consumer = scheduleStore::expire;
			var pair = Pair.of(id.getNum(), consumer);
			_shortLivedEntityExpiries.add(new AbstractMap.SimpleImmutableEntry<>(pair, schedule.expiry()));
		});

		_shortLivedEntityExpiries.sort(comparing(Map.Entry<Pair<Long, Consumer<EntityId>>, Long>::getValue).
				thenComparing(entry -> entry.getKey().getKey()));
		_shortLivedEntityExpiries.forEach(entry -> shortLivedEntityExpiries.track(entry.getKey(), entry.getValue()));
	}

	@Override
	public EntityId getAffectedEntityId() {
		// no-op for this job
		return null;
	}

	@Override
	public JobStatus getStatus() {
		return status;
	}

	public void setStatus(final JobStatus status) {
		this.status = status;
	}

	@Override
	public JobEntityClassification getClassification() {
		return classification;
	}

	private EntityId entityWith(long num) {
		return new EntityId(shard, realm, num);
	}
}
