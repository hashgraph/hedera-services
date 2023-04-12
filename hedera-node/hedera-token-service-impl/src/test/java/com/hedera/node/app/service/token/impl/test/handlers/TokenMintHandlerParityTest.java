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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertPreCheck;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_FOR_TOKEN_WITHOUT_SUPPLY;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_SUPPLY_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestoredToPbj;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.Test;

class TokenMintHandlerParityTest extends ParityTestBase {
    private final TokenMintHandler subject = new TokenMintHandler();

    @Test
    void tokenMintWithSupplyKeyedTokenScenario() throws PreCheckException {
        final var theTxn = txnFrom(MINT_WITH_SUPPLY_KEYED_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(sanityRestoredToPbj(context.payerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.requiredNonPayerKeys()), contains(TOKEN_SUPPLY_KT.asPbjKey()));
    }

    @Test
    void tokenMintWithMissingTokenScenario() throws PreCheckException {
        final var theTxn = txnFrom(MINT_WITH_MISSING_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        assertPreCheck(() -> subject.preHandle(context, readableTokenStore), INVALID_TOKEN_ID);
    }

    @Test
    void tokenMintWithoutSupplyScenario() throws PreCheckException {
        final var theTxn = txnFrom(MINT_FOR_TOKEN_WITHOUT_SUPPLY);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(sanityRestoredToPbj(context.payerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(0, context.requiredNonPayerKeys().size());
    }
}
