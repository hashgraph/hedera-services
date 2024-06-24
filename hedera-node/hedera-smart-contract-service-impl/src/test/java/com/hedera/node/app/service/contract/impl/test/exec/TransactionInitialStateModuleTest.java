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

package com.hedera.node.app.service.contract.impl.test.exec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.TransactionInitialStateModule;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionInitialStateModuleTest {
    @Mock
    private ReadableAccountStore readableAccountStore;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Mock
    private ReadableFileStore readableFileStore;

    @Mock
    private HandleContext context;

    @Mock
    private StoreFactory storeFactory;

    @Test
    void providesInitialState() {
        given(context.storeFactory()).willReturn(storeFactory);
        given(storeFactory.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(storeFactory.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(storeFactory.readableStore(ReadableFileStore.class)).willReturn(readableFileStore);
        assertSame(tokenServiceApi, TransactionInitialStateModule.provideInitialTokenServiceApi(context));
        assertSame(readableAccountStore, TransactionInitialStateModule.provideInitialAccountStore(context));
        assertSame(readableFileStore, TransactionInitialStateModule.provideInitialFileStore(context));
    }
}
