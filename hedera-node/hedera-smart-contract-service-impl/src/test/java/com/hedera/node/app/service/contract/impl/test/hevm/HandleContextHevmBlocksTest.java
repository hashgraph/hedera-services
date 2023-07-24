/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.contract.impl.hevm.HandleContextHevmBlocks;
import com.hedera.node.app.spi.workflows.HandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleContextHevmBlocksTest {
    @Mock
    private HandleContext context;

    private HandleContextHevmBlocks subject;

    @BeforeEach
    void setUp() {
        subject = new HandleContextHevmBlocks(context);
    }

    @Test
    void blockHashOfNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.blockHashOf(1L));
    }

    @Test
    void blockValuesOfNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.blockValuesOf(1L));
    }
}
