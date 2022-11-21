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
package com.hedera.node.app.hapi.utils.sysfiles.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ErrorCodeUtilsTest {
    @Test
    void usesTplForExceptionMsg() {
        final var details = "YIKES!";
        final var expectedMsg = "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: " + details;

        final var actualMsg =
                ErrorCodeUtils.exceptionMsgFor(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION, details);

        assertEquals(expectedMsg, actualMsg);
    }

    @Test
    void extractsErrorCodeFromMsg() {
        final var msg = "NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: YIKES!";

        assertEquals(
                Optional.of(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION),
                ErrorCodeUtils.errorFrom(msg));
    }

    @Test
    void returnsEmptyOptionalIfNoErrorCode() {
        final var msg = "YIKES!";

        assertEquals(Optional.empty(), ErrorCodeUtils.errorFrom(msg));
    }
}
