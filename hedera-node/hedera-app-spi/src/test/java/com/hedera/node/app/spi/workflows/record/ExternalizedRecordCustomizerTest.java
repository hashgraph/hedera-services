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

package com.hedera.node.app.spi.workflows.record;

import static com.hedera.node.app.spi.workflows.record.StreamBuilder.TransactionCustomizer.SUPPRESSING_TRANSACTION_CUSTOMIZER;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.Transaction;
import org.junit.jupiter.api.Test;

class ExternalizedRecordCustomizerTest {
    @Test
    void suppressionIsOffByDefault() {
        assertFalse(StreamBuilder.TransactionCustomizer.NOOP_TRANSACTION_CUSTOMIZER.isSuppressed());
    }

    @Test
    void suppressingCustomizerAsExpected() {
        assertTrue(SUPPRESSING_TRANSACTION_CUSTOMIZER.isSuppressed());
        assertThrows(
                UnsupportedOperationException.class,
                () -> SUPPRESSING_TRANSACTION_CUSTOMIZER.apply(Transaction.DEFAULT));
    }
}
