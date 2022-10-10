/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.exceptions.ResourceLimitException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResponseCodeUtilTest {
    @Test
    void translatesResourceLimitReversions() {
        for (final var status : ResponseCodeUtil.RESOURCE_EXHAUSTION_REVERSIONS.values()) {
            final var result = failureWithRevertReasonFrom(status);
            final var code = ResponseCodeUtil.getStatusOrDefault(result, OK);
            assertEquals(status, code);
        }
    }

    private TransactionProcessingResult failureWithRevertReasonFrom(final ResponseCodeEnum status) {
        final var ex = new ResourceLimitException(status);
        return TransactionProcessingResult.failed(
                1L, 2L, 3L, Optional.of(ex.messageBytes()), Optional.empty(), Map.of(), List.of());
    }
}
