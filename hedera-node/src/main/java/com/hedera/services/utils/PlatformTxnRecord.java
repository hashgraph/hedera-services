package com.hedera.services.utils;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.swirlds.common.Transaction;

import java.util.concurrent.TimeUnit;

public class PlatformTxnRecord {

    private Cache<Transaction, PlatformTxnAccessor> cache;
    private long timeToExpire;

    public PlatformTxnRecord(long timeToExpire) {
        this.timeToExpire = timeToExpire;
        cache = CacheBuilder.newBuilder()
                .expireAfterWrite(timeToExpire, TimeUnit.SECONDS)
                .build();
    }

    public PlatformTxnAccessor getPlatformTxnAccessor(Transaction platformTxn){
        if(platformTxn == null)
            return null;

        return cache.getIfPresent(platformTxn);

    }

    public void addTransaction(Transaction transaction) throws InvalidProtocolBufferException {
        PlatformTxnAccessor accessor = new PlatformTxnAccessor(transaction);
        cache.put(transaction, accessor);
    }

    public long getTimeToExpire() {
        return timeToExpire;
    }
}
