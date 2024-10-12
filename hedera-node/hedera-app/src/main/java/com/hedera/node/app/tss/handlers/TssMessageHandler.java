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

package com.hedera.node.app.tss.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.swirlds.common.exceptions.NotImplementedException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and responds to a {@link TssMessageTransactionBody}.
 */
@Singleton
public class TssMessageHandler implements TransactionHandler {
    private final AppContext.Gossip gossip;

    @Inject
    public TssMessageHandler(@NonNull final AppContext.Gossip gossip) {
        this.gossip = requireNonNull(gossip);
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        throw new NotImplementedException();
    }

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {
        throw new NotImplementedException();
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        throw new NotImplementedException();
    }
}
