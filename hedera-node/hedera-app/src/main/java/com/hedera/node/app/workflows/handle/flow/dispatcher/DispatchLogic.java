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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import static com.hedera.hapi.util.HapiUtils.isHollow;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DispatchLogic {
    private final Authorizer authorizer;
    private final SolvencyPreCheck solvencyPreCheck;

    @Inject
    public DispatchLogic(final Authorizer authorizer, final SolvencyPreCheck solvencyPreCheck) {
        this.authorizer = authorizer;
        this.solvencyPreCheck = solvencyPreCheck;
    }

    public void dispatch(Dispatch dispatch) {
        final var fees = dispatch.calculatedFees();

        if (dispatch.dueDiligenceInfo().dueDiligenceStatus() != ResponseCodeEnum.OK) {
            dispatch.recordBuilder().status(dispatch.dueDiligenceInfo().dueDiligenceStatus());
            dispatch.feeAccumulator()
                    .chargeNetworkFee(dispatch.dueDiligenceInfo().creator(), fees.networkFee());
        } else {
            final var payer = getPayer(dispatch);
            final var isPayerHollow = isHollow(payer);
            if (!isPayerHollow) {
                dispatch.keyVerifier().verificationFor(payer.keyOrThrow());
            }

            final var hasWaivedFees = authorizer.hasWaivedFees(
                    dispatch.syntheticPayer(),
                    dispatch.txnInfo().functionality(),
                    dispatch.txnInfo().txBody());
            if (!hasWaivedFees) {}
        }
    }

    private Account getPayer(final Dispatch dispatch) {
        try {
            return solvencyPreCheck.getPayerAccount(dispatch.storeFactory(), dispatch.syntheticPayer());
        } catch (Exception e) {
            throw new IllegalStateException("Missing payer should be a due diligence failure", e);
        }
    }
}
