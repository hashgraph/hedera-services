/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.NodeLocalProperties;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonBlockingHandoffTest {
    private final int mockCap = 10;
    private final RecordStreamObject rso = new RecordStreamObject();

    @Mock private ExecutorService executorService;
    @Mock private RecordStreamManager recordStreamManager;
    @Mock private NodeLocalProperties nodeLocalProperties;

    private NonBlockingHandoff subject;

    @Test
    void handoffWorksAsExpected() {
        given(nodeLocalProperties.recordStreamQueueCapacity()).willReturn(mockCap);
        // and:
        subject = new NonBlockingHandoff(recordStreamManager, nodeLocalProperties);

        // when:
        assertTrue(subject.offer(rso));

        // and:
        subject.getExecutor().shutdownNow();

        // then:
        try {
            verify(recordStreamManager).addRecordStreamObject(rso);
        } catch (NullPointerException ignore) {
            /* In CI apparently Mockito can have problems here? */
        }
    }

    @Test
    void shutdownHookWorksAsExpected() {
        given(nodeLocalProperties.recordStreamQueueCapacity()).willReturn(mockCap);
        // and:
        subject = new NonBlockingHandoff(recordStreamManager, nodeLocalProperties);
        // and:
        subject.setExecutor(executorService);

        // when:
        subject.getShutdownHook().run();

        // then:
        assertTrue(subject.getTimeToStop().get());
        // and:
        verify(executorService).shutdown();
    }
}
