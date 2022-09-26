/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicStoreTest {
    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    @Mock private TransactionRecordService transactionRecordService;

    private TopicStore subject;

    @BeforeEach
    void setup() {
        subject = new TopicStore(() -> topics, transactionRecordService);
    }

    @Test
    void persistNewAsExpected() {
        final var mockAutoRenewId = mock(Id.class);
        final var topic = new Topic(Id.DEFAULT);
        topic.setAutoRenewAccountId(mockAutoRenewId);
        given(mockAutoRenewId.asEntityId()).willReturn(EntityId.MISSING_ENTITY_ID);
        subject.persistNew(topic);

        verify(topics).put(any(), any());
        verify(mockAutoRenewId).asEntityId();
    }
}
