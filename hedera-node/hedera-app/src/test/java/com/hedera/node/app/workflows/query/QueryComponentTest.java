/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.query;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.DaggerHederaInjectionComponent;
import com.hedera.node.app.HederaInjectionComponent;
import com.hedera.node.app.components.QueryInjectionComponent;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableQueueState;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.EmptyIterator;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.gui.SwirldsGui;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryComponentTest {

    @Mock
    private Cryptography cryptography;

    @Mock
    private Platform platform;

    private HederaInjectionComponent app;

    @BeforeEach
    void setUp() {
        final var selfNodeId = new NodeId(666L);
        final Configuration configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        final PlatformContext platformContext = mock(PlatformContext.class);
        when(platformContext.getConfiguration()).thenReturn(configuration);
        when(platform.getContext()).thenReturn(platformContext);
        when(platformContext.getCryptography()).thenReturn(cryptography);
        app = DaggerHederaInjectionComponent.builder()
                .initTrigger(InitTrigger.GENESIS)
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .consoleCreator(SwirldsGui::createConsole)
                .staticAccountMemo("memo")
                .bootstrapProps(new BootstrapProperties())
                .selfId(AccountID.newBuilder()
                        .accountNum(selfNodeId.getIdAsInt())
                        .build())
                .initialHash(new Hash())
                .maxSignedTxnSize(1024)
                .genesisUsage(false)
                .build();

        // Simulate an empty (but iterable) state for the record cache
        final var emptyIterableQueueState = new EmptyIterableQueueState(new EmptyIterableQueueWritableStates());
        app.workingStateAccessor().setHederaState(emptyIterableQueueState);
    }

    @Test
    void objectGraphRootsAreAvailable() {
        given(platform.getSelfId()).willReturn(new NodeId(0L));

        final QueryInjectionComponent subject =
                app.queryComponentFactory().get().create();

        assertNotNull(subject.queryWorkflow());
    }

    // The following classes only exist to load enough of an empty state, such that the graph roots can be instantiated
    // for the test
    private record EmptyIterableQueueState(WritableStates writableStates) implements HederaState {
        @NotNull
        @Override
        public ReadableStates createReadableStates(@NotNull String serviceName) {
            return writableStates;
        }

        @NotNull
        @Override
        public WritableStates createWritableStates(@NotNull String serviceName) {
            return writableStates;
        }
    }

    private static final class EmptyIterableQueueWritableStates implements WritableStates {
        @NotNull
        @Override
        public <K, V> WritableKVState<K, V> get(@NotNull String stateKey) {
            return new MapWritableKVState<>(stateKey, Collections.emptyMap());
        }

        @NotNull
        @Override
        public <T> WritableSingletonState<T> getSingleton(@NotNull String stateKey) {
            return new WritableSingletonState<T>() {
                @Override
                public void put(@Nullable T value) {}

                @Override
                public boolean isModified() {
                    return false;
                }

                @NotNull
                @Override
                public String getStateKey() {
                    return "BOGUS STATE KEY";
                }

                @Nullable
                @Override
                public T get() {
                    return null;
                }

                @Override
                public boolean isRead() {
                    return false;
                }
            };
        }

        @NotNull
        @Override
        public <E> WritableQueueState<E> getQueue(@NotNull String stateKey) {
            return (WritableQueueState<E>) new EmptyWritableQueueState();
        }

        @Override
        public boolean contains(@NotNull String stateKey) {
            return false;
        }

        @NotNull
        @Override
        public Set<String> stateKeys() {
            return Set.of();
        }
    }

    private static final class EmptyWritableQueueState implements WritableQueueState<Object> {

        @NotNull
        @Override
        public String getStateKey() {
            return "BOGUS STATE KEY";
        }

        @Nullable
        @Override
        public Object peek() {
            return null;
        }

        @NotNull
        @Override
        public Iterator<Object> iterator() {
            return new EmptyIterator<>();
        }

        @Override
        public void add(@NotNull Object element) {}

        @Nullable
        @Override
        public Object removeIf(@NotNull Predicate predicate) {
            return null;
        }
    }
}
