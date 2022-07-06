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
package com.hedera.services.state;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.system.SwirldDualState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DualStateAccessorTest {
    @Mock private SwirldDualState dualState;

    @Test
    void beanMethodsWork() {
        // setup:
        final var subject = new DualStateAccessor();

        // expect:
        assertNull(subject.getDualState());

        // and when:
        subject.setDualState(dualState);

        // expect:
        Assertions.assertSame(dualState, subject.getDualState());
    }
}
