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

package com.hedera.node.app.tss.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TssVoteHandlerTest {
    @Mock
    private AppContext.Gossip gossip;

    @Mock
    private PreHandleContext preHandleContext;

    @Mock
    private HandleContext handleContext;

    private TssVoteHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TssVoteHandler(gossip);
    }

    @Test
    void nothingImplementedYet() {
        assertThrows(UnsupportedOperationException.class, () -> subject.preHandle(preHandleContext));
        assertThrows(UnsupportedOperationException.class, () -> subject.pureChecks(tssVote()));
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(handleContext));
    }

    private TransactionBody tssVote() {
        return TransactionBody.DEFAULT;
    }
}
