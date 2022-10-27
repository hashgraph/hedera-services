/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoPreTransactionHandlerNotImplTest {
    @Mock private AccountStore store;

    private CryptoPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        subject = new CryptoPreTransactionHandlerImpl(store);
    }

    @Test
    void notImplementedStuffIsntImplemented() {
        assertThrows(NotImplementedException.class, () -> subject.preHandleUpdateAccount(null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleCryptoTransfer(null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleCryptoDelete(null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleApproveAllowances(null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleDeleteAllowances(null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleAddLiveHash(null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleDeleteLiveHash(null));
    }
}
