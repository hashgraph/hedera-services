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

package com.hedera.node.app.service.consensus.impl.test.test;

import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.SIMPLE_TOPIC_ADMIN_KEY;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsensusCreateTopicHandlerParityTest {
    private final ConsensusCreateTopicHandler subject = new ConsensusCreateTopicHandler();
    private AccountKeyLookup keyLookup;

    @BeforeEach
    void setUp() {
        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccount() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertDefaultPayer(context);
        Assertions.assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccountWithCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertCustomPayer(context);
        Assertions.assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    void getsConsensusCreateTopicAdminKey() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertDefaultPayer(context);
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyWithCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertCustomPayer(context);
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccount() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertDefaultPayer(context);
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountWithCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertCustomPayer(context);
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        Assertions.assertThat(sanityRestored(context.getPayerKey())).isEqualTo(CUSTOM_PAYER_ACCOUNT_KT.asKey());
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsPayerWithCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertCustomPayer(context);
        // Note: DEFAULT_PAYER_KT in this case doesn't function as the payer - the payer is
        // CUSTOM_PAYER_ACCOUNT - but instead is in the required keys list because
        // DEFAULT_PAYER_KT is set as the auto-renew account
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey(), DEFAULT_PAYER_KT.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(context);
        assertCustomPayer(context);
        Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsCustomPayerWithCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO);

        // when:
        final var result = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(result);

        // then:
        ConsensusCreateTopicHandlerTest.assertOkResponse(result);
        assertCustomPayer(result);
        Assertions.assertThat(sanityRestored(result.getRequiredNonPayerKeys()))
                .containsExactly(SIMPLE_TOPIC_ADMIN_KEY.asKey());
    }

    @Test
    void invalidAutoRenewAccountOnConsensusCreateTopicThrows() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);
        subject.preHandle(context);

        // then:
        Assertions.assertThat(context.failed()).isTrue();
        Assertions.assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void invalidAutoRenewAccountOnConsensusCreateTopicThrowsWithCustomPayer() {
        // given:
        final var txn = txnFrom(CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO);

        // when:
        final var context = new PreHandleContext(keyLookup, txn, CUSTOM_PAYER_ACCOUNT);
        subject.preHandle(context);

        // then:
        Assertions.assertThat(context.failed()).isTrue();
        Assertions.assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            return fail(e);
        }
    }

    private void assertDefaultPayer(PreHandleContext context) {
        assertPayer(DEFAULT_PAYER_KT.asKey(), context);
    }

    private void assertCustomPayer(PreHandleContext context) {
        assertPayer(CUSTOM_PAYER_ACCOUNT_KT.asKey(), context);
    }

    private void assertPayer(Key expected, PreHandleContext context) {
        Assertions.assertThat(sanityRestored(context.getPayerKey())).isEqualTo(expected);
    }
}
