/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreFactoryImplTest {

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private WritableStoreFactory writableStoreFactory;

    @Mock
    private ServiceApiFactory serviceApiFactory;

    private StoreFactoryImpl subject;

    @BeforeEach
    void setUp() {
        subject = new StoreFactoryImpl(readableStoreFactory, writableStoreFactory, serviceApiFactory);
    }

    @Test
    void testCreateReadableStore() {
        // given
        final var result = mock(ReadableAccountStore.class);
        when(readableStoreFactory.getStore(ReadableAccountStore.class)).thenReturn(result);

        // when
        final var actual = subject.readableStore(ReadableAccountStore.class);

        // then
        assertThat(actual).isSameAs(result);
    }

    @Test
    void testCreateWritableStore() {
        // given
        final var result = mock(WritableAccountStore.class);
        when(writableStoreFactory.getStore(WritableAccountStore.class)).thenReturn(result);

        // when
        final var actual = subject.writableStore(WritableAccountStore.class);

        // then
        assertThat(actual).isSameAs(result);
    }

    @Test
    void testCreateServiceApi() {
        // given
        final var result = mock(TokenServiceApi.class);
        when(serviceApiFactory.getApi(TokenServiceApi.class)).thenReturn(result);

        // when
        final var actual = subject.serviceApi(TokenServiceApi.class);

        // then
        assertThat(actual).isSameAs(result);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testCreateStoreWithInvalidParameters() {
        assertThatThrownBy(() -> subject.readableStore(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.writableStore(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.serviceApi(null)).isInstanceOf(NullPointerException.class);
    }
}
