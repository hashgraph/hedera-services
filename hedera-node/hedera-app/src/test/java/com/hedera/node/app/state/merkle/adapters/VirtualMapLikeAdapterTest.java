/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.adapters;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.NFTS_KEY;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.state.merkle.adapters.VirtualMapLikeAdapter;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapCodecAdapter;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskKeySerializer;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.disk.OnDiskValueSerializer;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualMapLikeAdapterTest {
    private static final UniqueTokenKey A_KEY = new UniqueTokenKey(1234L, 5678L);
    private static final UniqueTokenKey B_KEY = new UniqueTokenKey(2345L, 6789L);
    private static final UniqueTokenKey C_KEY = new UniqueTokenKey(3456L, 7890L);
    private static final UniqueTokenKey D_KEY = new UniqueTokenKey(4567L, 8901L);
    private static final UniqueTokenKey Z_KEY = new UniqueTokenKey(7890L, 1234L);
    private static final UniqueTokenValue A_VALUE =
            new UniqueTokenValue(1L, 2L, "A".getBytes(), new RichInstant(1L, 2));
    private static final UniqueTokenValue B_VALUE =
            new UniqueTokenValue(2L, 3L, "B".getBytes(), new RichInstant(2L, 3));
    private static final UniqueTokenValue C_VALUE =
            new UniqueTokenValue(3L, 4L, "C".getBytes(), new RichInstant(3L, 4));
    private static final UniqueTokenValue D_VALUE =
            new UniqueTokenValue(4L, 5L, "D".getBytes(), new RichInstant(4L, 5));

    private VirtualMap<OnDiskKey<UniqueTokenKey>, OnDiskValue<UniqueTokenValue>> real;

    private VirtualMapLike<UniqueTokenKey, UniqueTokenValue> subject;

    private StateMetadata<UniqueTokenKey, UniqueTokenValue> metadata;

    @Mock
    private Metrics metrics;

    @Mock
    private InterruptableConsumer<Pair<UniqueTokenKey, UniqueTokenValue>> consumer;

    @Test
    void methodsDelegateAsExpected() throws IOException, InterruptedException {
        setupRealAndSubject();

        assertSame(real.getDataSource(), subject.getDataSource());
        assertSame(real.getHash(), subject.getHash());
        assertTrue(subject.isEmpty());

        putToReal(A_KEY, A_VALUE);
        putToReal(B_KEY, B_VALUE);
        putToReal(C_KEY, C_VALUE);

        assertNull(subject.get(Z_KEY));
        assertNull(subject.remove(Z_KEY));
        assertNull(subject.getForModify(Z_KEY));

        subject.extractVirtualMapData(getStaticThreadManager(), consumer, 1);
        verify(consumer).accept(Pair.of(A_KEY, A_VALUE));
        verify(consumer).accept(Pair.of(B_KEY, B_VALUE));
        verify(consumer).accept(Pair.of(C_KEY, C_VALUE));

        assertEquals(3, subject.size());

        assertFalse(subject.containsKey(D_KEY));
        subject.put(D_KEY, D_VALUE);
        assertTrue(subject.containsKey(D_KEY));
        assertEquals(D_VALUE, subject.get(D_KEY));
        subject.remove(B_KEY);
        assertFalse(subject.containsKey(B_KEY));

        final var mutableA = subject.getForModify(A_KEY);
        mutableA.setOwner(EntityId.fromNum(666L));

        assertDoesNotThrow(() -> subject.registerMetrics(metrics));

        real.copy();
        subject.release();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setupRealAndSubject() throws IOException {
        final var schema = justNftsSchema();
        final var nftsDef = schema.statesToCreate().iterator().next();
        metadata = new StateMetadata<>("REAL", schema, nftsDef);

        final var keySerializer = new OnDiskKeySerializer(metadata);
        final var valueSerializer = new OnDiskValueSerializer(metadata);
        final var ds = new JasperDbBuilder<>()
                .maxNumOfKeys(1_024)
                .storageDir(TemporaryFileBuilder.buildTemporaryDirectory("jasperdb"))
                .keySerializer(keySerializer)
                .virtualLeafRecordSerializer(new VirtualLeafRecordSerializer<>(
                        (short) 1,
                        DigestType.SHA_384,
                        (short) 1,
                        DataFileCommon.VARIABLE_DATA_SIZE,
                        keySerializer,
                        (short) 1,
                        DataFileCommon.VARIABLE_DATA_SIZE,
                        valueSerializer,
                        false));
        real = new VirtualMap<>("REAL", ds);
        subject = VirtualMapLikeAdapter.unwrapping(metadata, real);
    }

    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    private Schema justNftsSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(onDiskNftsDef());
            }
        };
    }

    private void putToReal(final UniqueTokenKey key, final UniqueTokenValue value) {
        real.put(new OnDiskKey<>(metadata, key), new OnDiskValue<>(metadata, value));
    }

    private StateDefinition<UniqueTokenKey, UniqueTokenValue> onDiskNftsDef() {
        final var keySerdes = MonoMapCodecAdapter.codecForVirtualKey(
                UniqueTokenKey.CURRENT_VERSION, UniqueTokenKey::new, new UniqueTokenKeySerializer());
        final var valueSerdes =
                MonoMapCodecAdapter.codecForVirtualValue(UniqueTokenValue.CURRENT_VERSION, UniqueTokenValue::new);
        return StateDefinition.onDisk(NFTS_KEY, keySerdes, valueSerdes, 1_024);
    }
}
