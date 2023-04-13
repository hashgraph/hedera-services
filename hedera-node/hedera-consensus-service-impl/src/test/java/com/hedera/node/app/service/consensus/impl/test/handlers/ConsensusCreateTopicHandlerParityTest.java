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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.SIMPLE_TOPIC_ADMIN_KEY;
import static com.hedera.test.utils.KeyUtils.sanityRestored;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsensusCreateTopicHandlerParityTest {
    private final ConsensusCreateTopicHandler subject = new ConsensusCreateTopicHandler();
    private AccountAccess keyLookup;

    @BeforeEach
    void setUp() {
        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccount() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO.pbjTxnBody();

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        // then:
        assertDefaultPayer(context);
        Assertions.assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    void getsConsensusCreateTopicAdminKey() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO.pbjTxnBody();

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        // then:
        assertDefaultPayer(context);
        Assertions.assertThat(sanityRestored(context.requiredNonPayerKeys()))
                .containsExactlyInAnyOrder(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccount() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO.pbjTxnBody();

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        // then:
        assertDefaultPayer(context);
        Assertions.assertThat(sanityRestored(context.requiredNonPayerKeys()))
                .containsExactlyInAnyOrder(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsPayerWithCustomPayer() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO.pbjTxnBody();

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        // then:
        assertDefaultPayer(context);
        // Note: DEFAULT_PAYER_KT in this case doesn't function as the payer - the payer is
        // CUSTOM_PAYER_ACCOUNT - but instead is in the required keys list because
        // DEFAULT_PAYER_KT is set as the auto-renew account
        Assertions.assertThat(sanityRestored(context.requiredNonPayerKeys()))
                .containsExactlyInAnyOrder(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void invalidAutoRenewAccountOnConsensusCreateTopicThrows() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO.pbjTxnBody();

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void invalidAutoRenewAccountOnConsensusCreateTopicThrowsWithCustomPayer() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO.pbjTxnBody();

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }
}
