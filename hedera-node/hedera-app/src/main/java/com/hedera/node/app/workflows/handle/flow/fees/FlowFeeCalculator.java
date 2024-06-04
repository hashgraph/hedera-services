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

package com.hedera.node.app.workflows.handle.flow.fees;

import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.dispatcher.ChildDispatchComponent;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public abstract class FlowFeeCalculator {
    private final Authorizer authorizer;
    private final TransactionDispatcher dispatcher;

    @Inject
    public FlowFeeCalculator(final Authorizer authorizer, final TransactionDispatcher dispatcher) {
        this.authorizer = authorizer;
        this.dispatcher = dispatcher;
    }

    public Fees calculateFees(final ChildDispatchComponent dispatch) {
        final var hasWaivedFees = authorizer.hasWaivedFees(
                dispatch.txnInfo().payerID(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (hasWaivedFees) {
            return Fees.FREE;
        }
        if (dispatch.origin() == ChildDispatchComponent.Origin.HAPI) {
            return dispatcher.dispatchComputeFees(dispatch.feeContext());
        } else {
            final var fees = dispatch.handleContext()
                    .dispatchComputeFees(
                            dispatch.txnInfo().txBody(), dispatch.syntheticPayer(), ComputeDispatchFeesAsTopLevel.YES);
            return fees.copyBuilder().networkFee(0).nodeFee(0).build();
        }
    }
}
