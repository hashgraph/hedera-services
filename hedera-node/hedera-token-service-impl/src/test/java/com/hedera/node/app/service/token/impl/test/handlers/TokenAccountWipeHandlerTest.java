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

import static com.hedera.test.factories.scenarios.TokenWipeScenarios.VALID_WIPE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.WIPE_FOR_TOKEN_WITHOUT_KEY;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.WIPE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_WIPE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.Test;

class TokenAccountWipeHandlerTest extends ParityTestBase {
    private final TokenAccountWipeHandler subject = new TokenAccountWipeHandler();

    @Test
    void tokenWipeWithValidExtantTokenScenario() {
        final var theTxn = txnFrom(VALID_WIPE_WITH_EXTANT_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(TOKEN_WIPE_KT.asKey()));
    }

    @Test
    void tokenWipeWithMissingTokenScenario() {
        final var theTxn = txnFrom(WIPE_WITH_MISSING_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertTrue(context.failed());
        assertEquals(INVALID_TOKEN_ID, context.getStatus());
        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(0, context.getRequiredNonPayerKeys().size());
    }

    @Test
    void tokenWipeWithoutKeyScenario() {
        final var theTxn = txnFrom(WIPE_FOR_TOKEN_WITHOUT_KEY);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(0, context.getRequiredNonPayerKeys().size());
    }
}
