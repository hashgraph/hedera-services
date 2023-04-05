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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.test.factories.scenarios.TokenUnpauseScenarios.VALID_UNPAUSE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_PAUSE_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestoredToPbj;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUnpauseHandlerParityTest extends ParityTestBase {
    private TokenUnpauseHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TokenUnpauseHandler();
    }

    @Test
    void tokenWipeWithValidExtantTokenScenario() {
        final var theTxn = txnFrom(VALID_UNPAUSE_WITH_EXTANT_TOKEN);

        final var context = new PreHandleContext(readableAccountStore, theTxn);
        subject.preHandle(context, readableTokenStore);

        assertFalse(context.failed());
        assertEquals(OK, context.getStatus());
        assertEquals(1, context.getRequiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_PAUSE_KT.asPbjKey()));
    }
}
