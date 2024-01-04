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

package com.hedera.node.app.service.contract.impl.state;

import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;

/**
 * A factory for {@link EvmFrameState} instances that are scoped to the current state of the world in
 * the ongoing transaction.
 */
public class ScopedEvmFrameStateFactory implements EvmFrameStateFactory {
    private final HederaOperations hederaOperations;
    private final HederaNativeOperations hederaNativeOperations;

    @Inject
    public ScopedEvmFrameStateFactory(
            @NonNull final HederaOperations hederaOperations,
            @NonNull final HederaNativeOperations hederaNativeOperations) {
        this.hederaOperations = Objects.requireNonNull(hederaOperations);
        this.hederaNativeOperations = Objects.requireNonNull(hederaNativeOperations);
    }

    @Override
    public EvmFrameState get() {
        return new DispatchingEvmFrameState(hederaNativeOperations, hederaOperations.getStore());
    }
}
