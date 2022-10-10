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
package com.hedera.services.queries;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class AnswerServiceTest {
    @Test
    void delegatesToNonQueryCtxAsExpected() {
        // setup:
        AnswerService subject = mock(AnswerService.class);

        // given:
        willCallRealMethod().given(subject).responseGiven(any(), any(), any(), anyLong(), anyMap());

        // when:
        subject.responseGiven(null, null, null, 0L, Collections.emptyMap());

        // then:
        verify(subject).responseGiven(any(), any(), any(), anyLong());
    }
}
