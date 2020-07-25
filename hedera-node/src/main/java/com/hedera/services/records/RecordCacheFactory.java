package com.hedera.services.records;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Minimal helper to construct a {@link RecordCache} based on the TTL
 * configured in the Hedera Services properties.
 *
 * @author Michael Tinker
 */
public class RecordCacheFactory {
	private static final Logger log = LogManager.getLogger(RecordCache.class);

	private final PropertySource properties;

	public RecordCacheFactory(PropertySource properties) {
		this.properties = properties;
	}

	public Cache<TransactionID, Boolean> getRecordCache() {
		int ttl = properties.getIntProperty("cache.records.ttl");

		log.info("Constructing the node-local txn id cache with ttl={}s", ttl);
		return CacheBuilder
				.newBuilder()
				.expireAfterWrite(ttl, TimeUnit.SECONDS)
				.build();
	}
}
