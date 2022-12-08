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
package com.hedera.node.app.spi;

import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Contextual information needed to perform pre-handle. Currently, provides extra information needed
 * for signing requirements using {@link HederaAccountNumbers} and {@link HederaFileNumbers} to
 * validate if any account's signature can be waived. Any {@link PreTransactionHandler} will need to
 * construct its own {@link SigWaivers} to check for signature waivers using the information
 * provided in this class.
 *
 * @param accountNumbers provides information about signature waiver special cases for some
 *     transactions
 */
public record PreHandleContext(
        @NonNull HederaAccountNumbers accountNumbers,
        @NonNull HederaFileNumbers fileNumbers,
        @NonNull AccountKeyLookup keyLookup) {
    public PreHandleContext {
        Objects.requireNonNull(accountNumbers);
        Objects.requireNonNull(fileNumbers);
        Objects.requireNonNull(keyLookup);
    }
}
