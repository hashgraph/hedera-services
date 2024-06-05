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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.handle.flow.DueDiligenceLogic;
import com.hedera.node.app.workflows.handle.flow.DueDiligenceReport;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DispatchLogic {
    private final Authorizer authorizer;
    private final SolvencyPreCheck solvencyPreCheck;
    private final DueDiligenceLogic dueDiligenceLogic;

    @Inject
    public DispatchLogic(
            final Authorizer authorizer,
            final SolvencyPreCheck solvencyPreCheck,
            final DueDiligenceLogic dueDiligenceLogic) {
        this.authorizer = authorizer;
        this.solvencyPreCheck = solvencyPreCheck;
        this.dueDiligenceLogic = dueDiligenceLogic;
    }

    /**
     * This method is responsible for charging the fees and executing the business logic for the given dispatch,
     * guaranteeing that the changes committed to its stack are exactly reflected in its recordBuilder.
     * @param dispatch the dispatch to be processed
     */
    public void dispatch(Dispatch dispatch) {
        final var dueDiligenceReport = dueDiligenceLogic.dueDiligenceReportFor(dispatch);

        if (dueDiligenceReport.isDueDiligenceFailure()) {
            chargeCreator(dispatch, dueDiligenceReport);
        } else {
            chargePayer(dispatch, dueDiligenceReport);
            if (dueDiligenceReport.payerSolvency() != OK) {
                dispatch.recordBuilder().status(dueDiligenceReport.payerSolvency());
            } else {

            }
        }

        // TODO: finalize record, commit changes to stack
    }

    private void chargeCreator(final Dispatch dispatch, DueDiligenceReport report) {
        dispatch.recordBuilder().status(report.dueDiligenceInfo().dueDiligenceStatus());
        dispatch.feeAccumulator()
                .chargeNetworkFee(
                        report.dueDiligenceInfo().creator(),
                        dispatch.calculatedFees().networkFee());
    }

    private void chargePayer(final Dispatch dispatch, DueDiligenceReport report) {
        final var hasWaivedFees = authorizer.hasWaivedFees(
                dispatch.syntheticPayer(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (hasWaivedFees) {
            return;
        }
        if (report.unableToPayServiceFee() || report.isDuplicate()) {
            dispatch.feeAccumulator()
                    .chargeFees(
                            report.payer().accountIdOrThrow(),
                            report.dueDiligenceInfo().creator(),
                            dispatch.calculatedFees().withoutServiceComponent());
        } else {
            dispatch.feeAccumulator()
                    .chargeFees(
                            report.payer().accountIdOrThrow(),
                            report.dueDiligenceInfo().creator(),
                            dispatch.calculatedFees());
        }
    }
}
