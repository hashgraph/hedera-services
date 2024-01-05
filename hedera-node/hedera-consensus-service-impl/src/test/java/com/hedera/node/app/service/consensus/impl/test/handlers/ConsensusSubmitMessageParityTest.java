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
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_SCENARIO;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ConsensusSubmitMessageParityTest extends ConsensusTestBase {
    @Mock
    private ReadableAccountStore accountStore;

    private ConsensusSubmitMessageHandler subject;

    @BeforeEach
    void setup() {
        subject = new ConsensusSubmitMessageHandler();
        readableStore = mock(ReadableTopicStore.class);
        accountStore = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsConsensusSubmitMessageNoSubmitKey() throws PreCheckException {
        final var txn = CONSENSUS_SUBMIT_MESSAGE_SCENARIO.pbjTxnBody();

        var topic = newTopic(null);
        given(readableStore.getTopic(notNull())).willReturn(topic);
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableTopicStore.class, readableStore);

        // when:
        assertDoesNotThrow(() -> subject.preHandle(context));

        // then:
        assertDefaultPayer(context);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    void getsConsensusSubmitMessageWithSubmitKey() throws PreCheckException {
        final var txn = CONSENSUS_SUBMIT_MESSAGE_SCENARIO.pbjTxnBody();
        final var key = A_COMPLEX_KEY;

        var topic = newTopic(key);
        given(readableStore.getTopic(notNull())).willReturn(topic);
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableTopicStore.class, readableStore);

        // when:
        assertDoesNotThrow(() -> subject.preHandle(context));

        // then:
        ConsensusTestUtils.assertDefaultPayer(context);
        assertThat(context.requiredNonPayerKeys()).isEqualTo(Set.of(key));
    }

    @Test
    void reportsConsensusSubmitMessageMissingTopic() throws PreCheckException {
        // given:
        final var txn = CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO.pbjTxnBody();

        given(readableStore.getTopic(notNull())).willReturn(null);
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableTopicStore.class, readableStore);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOPIC_ID);
    }

    private Topic newTopic(Key submit) {
        return ConsensusTestUtils.newTopic(null, submit);
    }
}
