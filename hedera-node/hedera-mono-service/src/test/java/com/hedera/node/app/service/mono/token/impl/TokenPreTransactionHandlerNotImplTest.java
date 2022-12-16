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
package com.hedera.node.app.service.mono.token.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.mono.state.logic.NetworkUtilization;
import com.hedera.node.app.spi.PreHandleContext;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenPreTransactionHandlerNotImplTest {

    @Mock
    private AccountStore accountStore;
    @Mock
    private PreHandleContext context;
    private TokenPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        subject = new TokenPreTransactionHandlerImpl(accountStore, context);
    }

    @Test
    void notImplementedStuffIsntImplemented() {
        assertThrows(NotImplementedException.class, () -> subject.preHandleAssociateTokens(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleBurnToken(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleDeleteToken(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleMintToken(null, null));
        assertThrows(
                NotImplementedException.class, () -> subject.preHandleFreezeTokenAccount(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleAssociateTokens(null, null));
        assertThrows(
                NotImplementedException.class, () -> subject.preHandleGrantKycToTokenAccount(null, null));
        assertThrows(
                NotImplementedException.class,
                () -> subject.preHandleRevokeKycFromTokenAccount(null, null));
        assertThrows(
                NotImplementedException.class, () -> subject.preHandleUnfreezeTokenAccount(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleWipeTokenAccount(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleUpdateToken(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleDissociateTokens(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandlePauseToken(null, null));
        assertThrows(NotImplementedException.class, () -> subject.preHandleUnpauseToken(null, null));
    }
}
