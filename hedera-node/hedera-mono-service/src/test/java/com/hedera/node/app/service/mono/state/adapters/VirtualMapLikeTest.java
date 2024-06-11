/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.adapters;

import static com.swirlds.common.io.utility.LegacyTemporaryFileBuilder.buildTemporaryDirectory;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValueSerializer;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.VirtualMap;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualMapLikeTest {
    private static final EntityNumVirtualKey A_LONG_KEY = new EntityNumVirtualKey(123L);
    private static final EntityNumVirtualKey B_LONG_KEY = new EntityNumVirtualKey(234L);
    private static final EntityNumVirtualKey C_LONG_KEY = new EntityNumVirtualKey(345L);
    private static final ScheduleVirtualValue A_LONG_VALUE = new ScheduleVirtualValue();
    private static final ScheduleVirtualValue B_LONG_VALUE = new ScheduleVirtualValue();
    private static final ScheduleVirtualValue C_LONG_VALUE = new ScheduleVirtualValue();

    static {
        A_LONG_VALUE.setPayer(new EntityId(0, 0, 3));
        B_LONG_VALUE.setPayer(new EntityId(0, 0, 4));
        C_LONG_VALUE.setPayer(new EntityId(0, 0, 5));
    }

    @Mock
    private Metrics metrics;

    @Mock
    private InterruptableConsumer<Pair<EntityNumVirtualKey, ScheduleVirtualValue>> consumer;

    private VirtualMap<EntityNumVirtualKey, ScheduleVirtualValue> real;

    private VirtualMapLike<EntityNumVirtualKey, ScheduleVirtualValue> subject;

    @Test
    void fromLongKeyedWorks() throws IOException, InterruptedException {
        setupRealAndSubject();

        Assertions.assertTrue(subject.isEmpty());

        putToReal(A_LONG_KEY, A_LONG_VALUE);
        putToReal(B_LONG_KEY, B_LONG_VALUE);
        putToReal(C_LONG_KEY, C_LONG_VALUE);

        Assertions.assertFalse(subject.isEmpty());

        subject.extractVirtualMapData(getStaticThreadManager(), consumer, 1);

        verify(consumer).accept(Pair.of(A_LONG_KEY, A_LONG_VALUE));
        verify(consumer).accept(Pair.of(B_LONG_KEY, B_LONG_VALUE));
        verify(consumer).accept(Pair.of(C_LONG_KEY, C_LONG_VALUE));

        assertDoesNotThrow(() -> subject.registerMetrics(metrics));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setupRealAndSubject() throws IOException {
        final var keySerializer = new EntityNumVirtualKeySerializer();
        final var valueSerializer = new ScheduleVirtualValueSerializer();
        final var tableConfig = new MerkleDbTableConfig<>(
                        (short) 1,
                        DigestType.SHA_384,
                        (short) 1,
                        keySerializer,
                        (short) 1,
                        valueSerializer)
                .maxNumberOfKeys(1_024);

        final var ds = new MerkleDbDataSourceBuilder<>(
                buildTemporaryDirectory("merkleDbTest"), keySerializer, valueSerializer, tableConfig);

        real = new VirtualMap<>("REAL", ds);
        subject = VirtualMapLike.from(real);
    }

    private void putToReal(final EntityNumVirtualKey key, final ScheduleVirtualValue value) {
        real.put(key, value);
    }
}
