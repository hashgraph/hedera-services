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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Basic implementation support for a {@link HtsCallTranslator} that returns a translated
 * call when the {@link HtsCallAttempt} matches and null otherwise.
 */
public abstract class AbstractHtsCallTranslator implements HtsCallTranslator {
    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable HtsCall translateCallAttempt(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        if (matches(attempt)) {
            return callFrom(attempt);
        }
        return null;
    }

    /**
     * Returns true if the attempt matches the selector of the call this translator is responsible for.
     *
     * @param attempt the selector to match
     * @return true if the selector matches the selector of the call this translator is responsible for
     */
    public abstract boolean matches(@NonNull HtsCallAttempt attempt);

    /**
     * Returns a call from the given attempt.
     *
     * @param attempt the attempt to get the call from
     * @return a call from the given attempt
     */
    public abstract HtsCall callFrom(@NonNull HtsCallAttempt attempt);
}
