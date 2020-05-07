package com.hedera.services.txns.diligence;

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
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Implements a {@link DuplicateClassifier} that counts the number of
 * times within a window of width <tt>cache.records.ttl</tt> seconds
 * that a given {@link TransactionID} has been observed.
 *
 * @author Michael Tinker
 */
public class CountingDuplicateClassifier implements DuplicateClassifier {
	private final PropertySource properties;

	final Map<TransactionID, Integer> observedCounts;
	final BlockingQueue<DuplicateIdHorizon> horizons;

	public CountingDuplicateClassifier(
			PropertySource properties,
			Map<TransactionID, Integer> observedCounts,
			BlockingQueue<DuplicateIdHorizon> horizons
	) {
		this.horizons = horizons;
		this.properties = properties;
		this.observedCounts = observedCounts;
	}

	@Override
	public void observe(TransactionID txnId, long at) {
		int ttl = properties.getIntProperty("cache.records.ttl");
		horizons.offer(new DuplicateIdHorizon(at + ttl, txnId));
		observedCounts.merge(txnId, 1, Math::addExact);
	}

	@Override
	public void shiftWindow(long to) {
		while (!horizons.isEmpty() && (horizons.peek().getHorizon() < to)) {
			DuplicateIdHorizon sunset = horizons.poll();
			TransactionID id = sunset.getTxnId();

			observedCounts.merge(id, -1, Math::addExact);
			if (observedCounts.get(id) <= 0) {
				observedCounts.remove(id);
			}
		}
	}

	@Override
	public boolean isDuplicate(TransactionID txnId) {
		return observedCounts.containsKey(txnId);
	}
}
