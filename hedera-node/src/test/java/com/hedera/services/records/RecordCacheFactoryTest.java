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
import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({LogCaptureExtension.class})
class RecordCacheFactoryTest {
    private static final TransactionID txnIdA =
            TransactionID.newBuilder().setAccountID(asAccount("0.0.2")).build();
    private static final TransactionID txnIdB =
            TransactionID.newBuilder().setAccountID(asAccount("2.2.0")).build();

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private RecordCacheFactory subject;

    private PropertySource properties;

    @BeforeEach
    void setUp() {
        properties = mock(PropertySource.class);
        given(properties.getIntProperty(CACHE_RECORDS_TTL)).willReturn(1);

        subject = new RecordCacheFactory(properties);
    }

    @Test
    void hasExpectedExpiry() {
        final var cache = subject.getCache();
        cache.put(txnIdA, RecordCache.MARKER);

        assertEquals(RecordCache.MARKER, cache.getIfPresent(txnIdA));
        assertNull(cache.getIfPresent(txnIdB));
        SLEEPING_PAUSE.forMs(50L);
        assertEquals(RecordCache.MARKER, cache.getIfPresent(txnIdA));
        SLEEPING_PAUSE.forMs(1000L);
        assertNull(cache.getIfPresent(txnIdA));
        assertThat(
                logCaptor.infoLogs(),
                contains("Constructing the node-local txn id cache with ttl=1s"));
    }
}
