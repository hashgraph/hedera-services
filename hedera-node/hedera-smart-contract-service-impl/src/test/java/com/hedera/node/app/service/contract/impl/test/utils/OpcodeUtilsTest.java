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

package com.hedera.node.app.service.contract.impl.test.utils;

import static com.hedera.node.app.service.contract.impl.utils.OpcodeUtils.asCallOperationType;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.streams.CallOperationType;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpcodeUtilsTest {
    @ParameterizedTest
    @CsvSource({
        "0xF0,OP_CREATE",
        "0xF1,OP_CALL",
        "0xF2,OP_CALLCODE",
        "0xF4,OP_DELEGATECALL",
        "0xF5,OP_CREATE2",
        "0xFA,OP_STATICCALL",
        "0x12,OP_UNKNOWN",
    })
    void convertsOpcodesToCallOpsAsExpected(final int opCode, @NonNull final CallOperationType type) {
        assertEquals(type, asCallOperationType(opCode));
    }
}
