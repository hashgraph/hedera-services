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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.Key;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link VerificationStrategy} that verifies signatures as if both of two contracts are "active; but never
 * using top-level signatures.
 *
 * <p>This is the verification strategy used to support the {@code contracts.keys.legacyActivations} property.
 */
public class EitherOrVerificationStrategy implements VerificationStrategy {
    private final VerificationStrategy firstStrategy;
    private final VerificationStrategy secondStrategy;

    public EitherOrVerificationStrategy(
            @NonNull final VerificationStrategy firstStrategy, @NonNull final VerificationStrategy secondStrategy) {
        this.firstStrategy = firstStrategy;
        this.secondStrategy = secondStrategy;
    }

    @Override
    public Decision decideForPrimitive(@NonNull final Key key) {
        return firstStrategy.decideForPrimitive(key) == Decision.VALID
                        || secondStrategy.decideForPrimitive(key) == Decision.VALID
                ? Decision.VALID
                : Decision.INVALID;
    }
}
