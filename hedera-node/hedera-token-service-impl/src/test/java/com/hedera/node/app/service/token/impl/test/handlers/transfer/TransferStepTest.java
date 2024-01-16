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

package com.hedera.node.app.service.token.impl.test.handlers.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.token.impl.handlers.transfer.TransferStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferStepTest extends StepsBase {
    private TransferStep transferStep = transferContext -> {
        throw new UnsupportedOperationException();
    };

    @Test
    void assertKeysEmpty() {
        assertThat(transferStep.authorizingKeysIn(transferContext)).isEmpty();
    }
}
