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

package com.hedera.node.app.service.contract.impl.state;

import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtFrameScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtWorldScope;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import java.util.Objects;

/**
 * A factory for {@link EvmFrameState} instances that are scoped to the current state of the world in
 * the ongoing transaction.
 */
@TransactionScope
public class InScopeFrameStateFactory implements EvmFrameStateFactory {
    private final HandleExtWorldScope scope;
    private final HandleExtFrameScope extFrameScope;

    @Inject
    public InScopeFrameStateFactory(@NonNull final HandleExtWorldScope scope, @NonNull final HandleExtFrameScope extFrameScope) {
        this.scope = Objects.requireNonNull(scope);
        this.extFrameScope = Objects.requireNonNull(extFrameScope);
    }

    @Override
    public EvmFrameState get() {
        return new ScopedEvmFrameState(
                extFrameScope,
                scope.getStore(),
                scope.getStore().storage(),
                scope.getStore().bytecode());
    }
}
