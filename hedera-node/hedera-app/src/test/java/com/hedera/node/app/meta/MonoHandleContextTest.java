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

package com.hedera.node.app.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoHandleContextTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private EntityIdSource ids;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private OptionValidator optionValidator;

    @Mock
    private TransactionContext txnCtx;

    private MonoHandleContext subject;

    @BeforeEach
    void setup() {
        subject = new MonoHandleContext(ids, expiryValidator, attributeValidator, txnCtx);
    }

    @Test
    void getsNowFromCtx() {
        given(txnCtx.consensusTime()).willReturn(NOW);

        assertEquals(NOW, subject.consensusNow());
    }

    @Test
    void delegatesIdCreationToEntitySource() {
        final var nextNum = 666L;
        given(ids.newAccountNumber()).willReturn(nextNum);

        final var numSupplier = subject.newEntityNumSupplier();

        assertEquals(nextNum, numSupplier.getAsLong());
    }

    @Test
    void returnsExpiryValidatorAsExpected() {
        assertSame(expiryValidator, subject.expiryValidator());
    }
}
