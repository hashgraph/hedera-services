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

package com.hedera.node.app.workflows.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.WritableFreezeStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableStoreFactoryImplTest {

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableKVState<Object, Object> writableKVState;

    @Mock
    private SavepointStackImpl stack;

    private static Stream<Arguments> storeParameters() {
        return Stream.of(
                arguments(AddressBookService.NAME, WritableNodeStore.class),
                arguments(ConsensusService.NAME, WritableTopicStore.class),
                arguments(TokenService.NAME, WritableAccountStore.class),
                arguments(TokenService.NAME, WritableNftStore.class),
                arguments(TokenService.NAME, WritableTokenStore.class),
                arguments(TokenService.NAME, WritableTokenRelationStore.class),
                arguments(FreezeService.NAME, WritableFreezeStore.class),
                arguments(AddressBookService.NAME, WritableNodeStore.class),
                arguments(FileService.NAME, WritableFileStore.class));
    }

    @BeforeEach
    void setUp() {
        lenient().when(writableStates.get(anyString())).thenReturn(writableKVState);
    }

    @ParameterizedTest
    @MethodSource("storeParameters")
    void returnCorrectStoreClass(final String serviceName, final Class<?> storeClass) {
        // given
        given(stack.getWritableStates(serviceName)).willReturn(writableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        final WritableStoreFactory subject =
                new WritableStoreFactory(stack, serviceName, configuration, mock(StoreMetricsService.class));

        // given
        final var store = subject.getStore(storeClass);

        // then
        assertThat(store).isInstanceOf(storeClass);
    }
}
