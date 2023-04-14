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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.TOKEN_FEE_SCHEDULE_KT;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestoredToPbj;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenFeeScheduleUpdateHandlerParityTest extends ParityTestBase {

    private final TokenFeeScheduleUpdateHandler subject = new TokenFeeScheduleUpdateHandler();

    @Test
    void tokenFeeScheduleUpdateNonExistingToken() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST);
        final var context = new PreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, readableTokenStore), INVALID_TOKEN_ID);
    }

    @Test
    void tokenFeeScheduleUpdateTokenWithoutFeeScheduleKey() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        // may look odd, but is intentional --- we fail in the handle(), not in preHandle()
        assertEquals(sanityRestoredToPbj(context.payerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(Collections.emptySet(), context.requiredNonPayerKeys());
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeySigReqFeeCollector() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(sanityRestoredToPbj(context.payerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.requiredNonPayerKeys()),
                containsInAnyOrder(TOKEN_FEE_SCHEDULE_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeySigNotReqFeeCollector() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(sanityRestoredToPbj(context.payerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.requiredNonPayerKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asPbjKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndOneSigReqFeeCollectorAndAnotherSigNonReqFeeCollector()
            throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(sanityRestoredToPbj(context.payerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(2, context.requiredNonPayerKeys().size());
        assertThat(
                sanityRestoredToPbj(context.requiredNonPayerKeys()),
                containsInAnyOrder(TOKEN_FEE_SCHEDULE_KT.asPbjKey(), RECEIVER_SIG_KT.asPbjKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayerAndSigReq() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(sanityRestoredToPbj(context.payerKey()), RECEIVER_SIG_KT.asPbjKey());
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.requiredNonPayerKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asPbjKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayer() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER);
        final var context = new PreHandleContext(readableAccountStore, txn);
        subject.preHandle(context, readableTokenStore);

        assertEquals(List.of(NO_RECEIVER_SIG_KT.asPbjKey()), sanityRestoredToPbj(List.of(context.payerKey())));
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertThat(sanityRestoredToPbj(context.requiredNonPayerKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asPbjKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndInvalidFeeCollector() throws PreCheckException {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR);
        final var context = new PreHandleContext(readableAccountStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, readableTokenStore), INVALID_CUSTOM_FEE_COLLECTOR);
    }
}
