/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.services.bdd.spec.assertions;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.StateSignatureCallbackAsserts.StateSignatureTransactionCallbackMock;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;

public class StateSignatureCallbackAsserts implements ErroringAssertsProvider<StateSignatureTransactionCallbackMock>{

    @Override
    public ErroringAsserts<StateSignatureTransactionCallbackMock> assertsFor(HapiSpec spec) {
        return callback -> {
            try {
                Assertions.assertEquals(1, callback.counter.get());
            } catch (Throwable t) {
                return List.of(t);
            }
            return List.of();
        };
    }

    public static class StateSignatureTransactionCallbackMock implements
            Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>> {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public void accept(List<ScopedSystemTransaction<StateSignatureTransaction>> scopedSystemTransactions) {
            counter.incrementAndGet();
        }
    }
}
