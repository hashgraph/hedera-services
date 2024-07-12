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

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStoreImpl;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusTestBase {
    private static final String A_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B_NAME = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String C_NAME = "cccccccccccccccccccccccccccccccc";
    private static final Function<String, Key.Builder> KEY_BUILDER =
            value -> Key.newBuilder().ed25519(Bytes.wrap(value.getBytes()));
    public static final Key A_THRESHOLD_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    KEY_BUILDER.apply(C_NAME).build())
                            .build()))
            .build();
    public static final Key A_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_THRESHOLD_KEY)))
            .build();
    public static final Key B_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_COMPLEX_KEY)))
            .build();
    protected final Key key = A_COMPLEX_KEY;
    protected final Key anotherKey = B_COMPLEX_KEY;
    protected final AccountID payerId = AccountID.newBuilder().accountNum(3).build();
    public static final AccountID anotherPayer =
            AccountID.newBuilder().accountNum(13257).build();
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(1).build();
    protected final byte[] runningHash = "runningHash".getBytes();

    protected final Key adminKey = key;
    protected final Key autoRenewKey = anotherKey;
    protected final long topicEntityNum = 1L;
    protected final TopicID topicId =
            TopicID.newBuilder().topicNum(topicEntityNum).build();
    protected final Duration WELL_KNOWN_AUTO_RENEW_PERIOD =
            Duration.newBuilder().seconds(100).build();
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long sequenceNumber = 1L;
    protected final long autoRenewSecs = 100L;
    protected final Instant consensusTimestamp = Instant.ofEpochSecond(1_234_567L);

    protected Topic topic;

    protected Topic topicNoKeys;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock(strictness = LENIENT)
    protected HandleContext handleContext;

    @Mock(strictness = LENIENT)
    protected StoreFactory storeFactory;

    @Mock
    private StoreMetricsService storeMetricsService;

    protected MapReadableKVState<TopicID, Topic> readableTopicState;
    protected MapWritableKVState<TopicID, Topic> writableTopicState;

    protected ReadableTopicStore readableStore;
    protected WritableTopicStore writableStore;

    @BeforeEach
    void commonSetUp() {
        givenValidTopic();
        refreshStoresWithCurrentTopicOnlyInReadable();
    }

    protected void refreshStoresWithCurrentTopicOnlyInReadable() {
        readableTopicState = readableTopicState();
        writableTopicState = emptyWritableTopicState();
        given(readableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(readableTopicState);
        given(writableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableTopicStore(writableStates, configuration, storeMetricsService);
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableTopicStore.class)).willReturn(writableStore);
    }

    protected void refreshStoresWithCurrentTopicInBothReadableAndWritable() {
        readableTopicState = readableTopicState();
        writableTopicState = writableTopicStateWithOneKey();
        given(readableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(readableTopicState);
        given(writableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableTopicStore(writableStates, configuration, storeMetricsService);
        given(storeFactory.writableStore(WritableTopicStore.class)).willReturn(writableStore);
    }

    @NonNull
    protected MapWritableKVState<TopicID, Topic> emptyWritableTopicState() {
        return MapWritableKVState.<TopicID, Topic>builder(TOPICS_KEY).build();
    }

    @NonNull
    protected MapWritableKVState<TopicID, Topic> writableTopicStateWithOneKey() {
        return MapWritableKVState.<TopicID, Topic>builder(TOPICS_KEY)
                .value(topicId, topic)
                .build();
    }

    @NonNull
    protected MapReadableKVState<TopicID, Topic> readableTopicState() {
        return MapReadableKVState.<TopicID, Topic>builder(TOPICS_KEY)
                .value(topicId, topic)
                .build();
    }

    @NonNull
    protected MapReadableKVState<TopicID, Topic> emptyReadableTopicState() {
        return MapReadableKVState.<TopicID, Topic>builder(TOPICS_KEY).build();
    }

    protected void givenValidTopic() {
        givenValidTopic(autoRenewId);
    }

    protected void givenValidTopic(AccountID autoRenewAccountId) {
        givenValidTopic(autoRenewAccountId, false);
    }

    protected void givenValidTopic(AccountID autoRenewAccountId, boolean deleted) {
        givenValidTopic(autoRenewAccountId, deleted, true, true);
    }

    protected void givenValidTopic(AccountID autoRenewAccountId, boolean deleted, boolean withAdminKey) {
        givenValidTopic(autoRenewAccountId, deleted, withAdminKey, true);
    }

    protected void givenValidTopic(
            AccountID autoRenewAccountId, boolean deleted, boolean withAdminKey, boolean withSubmitKey) {
        topic = new Topic(
                topicId,
                sequenceNumber,
                expirationTime,
                autoRenewSecs,
                autoRenewAccountId,
                deleted,
                Bytes.wrap(runningHash),
                memo,
                withAdminKey ? key : null,
                withSubmitKey ? key : null);
        topicNoKeys = new Topic(
                topicId,
                sequenceNumber,
                expirationTime,
                autoRenewSecs,
                autoRenewAccountId,
                deleted,
                Bytes.wrap(runningHash),
                memo,
                null,
                null);
    }

    protected Topic createTopic() {
        return new Topic.Builder()
                .topicId(topicId)
                .adminKey(key)
                .submitKey(key)
                .autoRenewPeriod(autoRenewSecs)
                .autoRenewAccountId(autoRenewId)
                .expirationSecond(expirationTime)
                .sequenceNumber(sequenceNumber)
                .memo(memo)
                .deleted(true)
                .runningHash(Bytes.wrap(runningHash))
                .build();
    }
}
