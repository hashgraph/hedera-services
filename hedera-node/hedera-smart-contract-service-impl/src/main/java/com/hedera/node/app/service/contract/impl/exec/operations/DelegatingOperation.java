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

package com.hedera.node.app.service.contract.impl.exec.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * A delegating operation that delegates everything; used to eliminate duplication between
 * {@link CustomSStoreOperation} and {@link CustomSLoadOperation}.
 */
public class DelegatingOperation implements Operation {
    private final Operation delegate;

    /**
     * @param delegate the given operation
     */
    public DelegatingOperation(@NonNull final Operation delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        return delegate.execute(frame, evm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOpcode() {
        return delegate.getOpcode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStackItemsConsumed() {
        return delegate.getStackItemsConsumed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStackItemsProduced() {
        return delegate.getStackItemsProduced();
    }
}
