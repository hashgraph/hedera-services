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

import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_FOR_TOKEN_WITHOUT_SUPPLY;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_SUPPLY_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.Test;

public class TokenBurnHandlerParityTest extends ParityTestBase {
    private final TokenBurnHandler subject = new TokenBurnHandler();

    @Test
    void getsTokenBurnWithValidId() {
        final var theTxn = txnFrom(BURN_WITH_SUPPLY_KEYED_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_SUPPLY_KT.asKey()));
    }

    @Test
    void getsTokenBurnWithMissingToken() {
        final var theTxn = txnFrom(BURN_WITH_MISSING_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertTrue(context.failed());
        assertEquals(INVALID_TOKEN_ID, context.getStatus());
        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(0, context.getRequiredNonPayerKeys().size());
    }

    @Test
    void getsTokenBurnWithoutSupplyKey() {
        final var theTxn = txnFrom(BURN_FOR_TOKEN_WITHOUT_SUPPLY);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(0, context.getRequiredNonPayerKeys().size());
    }
}
