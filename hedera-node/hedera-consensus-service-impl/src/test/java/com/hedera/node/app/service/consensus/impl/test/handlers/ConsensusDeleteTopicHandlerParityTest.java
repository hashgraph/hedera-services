/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.A_NONNULL_KEY;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.newTopic;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_TOPIC_ADMIN_KT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusDeleteTopicHandlerParityTest {
    @Mock
    private ReadableTopicStore mockStore;

    private ConsensusDeleteTopicHandler subject;
    private ReadableAccountStore accountStore;

    @BeforeEach
    void setup() {
        subject = new ConsensusDeleteTopicHandler();
        accountStore = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsConsensusDeleteTopicNoAdminKey() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_DELETE_TOPIC_SCENARIO.pbjTxnBody();

        var topic = newTopic(null, A_NONNULL_KEY); // any submit key that isn't null
        given(mockStore.getTopic(notNull())).willReturn(topic);
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableTopicStore.class, mockStore);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), UNAUTHORIZED);
    }

    @Test
    void getsConsensusDeleteTopicWithAdminKey() throws Throwable {
        // given:
        final var txn = CONSENSUS_DELETE_TOPIC_SCENARIO.pbjTxnBody();
        var topic = newTopic(MISC_TOPIC_ADMIN_KT.asPbjKey(), null); // any submit key
        given(mockStore.getTopic(notNull())).willReturn(topic);
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableTopicStore.class, mockStore);

        // when:
        assertDoesNotThrow(() -> subject.preHandle(context));

        // then:
        assertDefaultPayer(context);
        Assertions.assertThat(context.requiredNonPayerKeys()).containsExactly(MISC_TOPIC_ADMIN_KT.asPbjKey());
    }

    @Test
    void reportsConsensusDeleteTopicMissingTopic() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO.pbjTxnBody();
        given(mockStore.getTopic(notNull())).willReturn(null);
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableTopicStore.class, mockStore);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOPIC_ID);
    }
}
