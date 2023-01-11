/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.files.sysfiles;

import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.verify;

import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.test.utils.SerdeUtils;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottlesCallbackTest {
    @Mock MultiplierSources multiplierSources;
    @Mock FunctionalityThrottling hapiThrottling;
    @Mock FunctionalityThrottling handleThrottling;
    @Mock FunctionalityThrottling scheduleThrottling;

    ThrottlesCallback subject;

    @BeforeEach
    void setUp() {
        subject =
                new ThrottlesCallback(
                        multiplierSources, hapiThrottling, handleThrottling, scheduleThrottling);
    }

    @Test
    void throttlesCbAsExpected() throws IOException {
        final var throttles = SerdeUtils.protoDefs("bootstrap/throttles.json");

        // when:
        subject.throttlesCb().accept(throttles);

        // then:
        verify(hapiThrottling).rebuildFor(argThat(pojo -> pojo.toProto().equals(throttles)));
        verify(handleThrottling).rebuildFor(argThat(pojo -> pojo.toProto().equals(throttles)));
        verify(scheduleThrottling).rebuildFor(argThat(pojo -> pojo.toProto().equals(throttles)));
        verify(multiplierSources).resetExpectations();
    }
}
