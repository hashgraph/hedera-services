/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.WorkflowException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_ADD_LIVE_HASH}.
 *
 * This transaction type is not currently supported. It is reserved for future use.
 */
@Singleton
public class CryptoAddLiveHashHandler implements TransactionHandler {
    /**
     * Default constructor for injection.
     */
    @Inject
    public CryptoAddLiveHashHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        throw new WorkflowException(ResponseCodeEnum.NOT_SUPPORTED);
    }

    @Override
    public void pureChecks(@NonNull final PureChecksContext context) {
        // nothing to do
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws WorkflowException {
        // this will never actually get called
        // because preHandle will throw a WorkflowException
        // before we get here
        throw new WorkflowException(ResponseCodeEnum.NOT_SUPPORTED);
    }

    @NonNull
    @Override
    public Fees calculateFees(final FeeContext feeContext) {
        return Fees.FREE;
    }
}
