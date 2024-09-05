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

package com.hedera.node.app.service.schedule.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Predicate for child dispatch key validation required because {@link HandleContext} no longer
 * allows a {@link VerificationAssistant} to be used for dispatch.
 */
public class DispatchPredicate implements Predicate<Key> {
    private final Set<Key> preValidatedKeys;

    /**
     * Create a new DispatchPredicate using the given set of keys as deemed-valid.
     *
     * @param preValidatedKeys an <strong>unmodifiable</strong> {@code Set<Key>} of primitive keys
     *         previously verified.
     */
    public DispatchPredicate(@NonNull final Set<Key> preValidatedKeys) {
        this.preValidatedKeys = requireNonNull(preValidatedKeys);
    }

    @Override
    public boolean test(@NonNull final Key key) {
        return preValidatedKeys.contains(requireNonNull(key));
    }
}
