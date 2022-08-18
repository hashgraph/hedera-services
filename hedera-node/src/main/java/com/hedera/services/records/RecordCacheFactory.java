/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.records;

import static com.hedera.services.context.properties.PropertyNames.CACHE_RECORDS_TTL;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minimal helper to construct a {@link RecordCache} based on the TTL configured in the Hedera
 * Services properties.
 */
@Singleton
public final class RecordCacheFactory {
    private static final Logger log = LogManager.getLogger(RecordCacheFactory.class);

    private final PropertySource properties;

    @Inject
    public RecordCacheFactory(final @CompositeProps PropertySource properties) {
        this.properties = properties;
    }

    public Cache<TransactionID, Boolean> getCache() {
        final var ttl = properties.getIntProperty(CACHE_RECORDS_TTL);

        log.info("Constructing the node-local txn id cache with ttl={}s", ttl);
        return CacheBuilder.newBuilder().expireAfterWrite(ttl, TimeUnit.SECONDS).build();
    }
}
