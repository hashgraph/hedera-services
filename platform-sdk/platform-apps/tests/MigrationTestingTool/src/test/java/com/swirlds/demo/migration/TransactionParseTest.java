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

package com.swirlds.demo.migration;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.security.SignatureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TransactionParseTest {

    @ParameterizedTest
    @ValueSource(longs = {5, 10})
    void test(final long seed) throws SignatureException {
        final TransactionGenerator generator = new TransactionGenerator(seed);
        final byte[] bytes = generator.generateTransaction();
        Assertions.assertDoesNotThrow(() -> TransactionUtils.parseTransaction(Bytes.wrap(bytes)));
    }
}
