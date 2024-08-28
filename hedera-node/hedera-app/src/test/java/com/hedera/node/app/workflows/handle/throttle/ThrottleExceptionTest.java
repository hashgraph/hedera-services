/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.throttle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.THROTTLED_AT_CONSENSUS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

class ThrottleExceptionTest {
    @Test
    void responseCodesAsExpected() {
        assertThat(ThrottleException.newGasThrottleException().getStatus()).isEqualTo(CONSENSUS_GAS_EXHAUSTED);
        assertThat(ThrottleException.newNativeThrottleException().getStatus()).isEqualTo(THROTTLED_AT_CONSENSUS);
    }
}
