/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.solvency;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.txns.submission.SolvencyPrecheck;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@link SolvencyPreCheck} that delegates to an instance of
 * {@link com.hedera.node.app.service.mono.txns.submission.SolvencyPrecheck}.
 */
@Singleton
public class MonoSolvencyPreCheck implements SolvencyPreCheck {

    private final com.hedera.node.app.service.mono.txns.submission.SolvencyPrecheck delegate;

    @Inject
    public MonoSolvencyPreCheck(SolvencyPrecheck delegate) {
        this.delegate = delegate;
    }

    @Override
    public void checkPayerAccountStatus(@NonNull final HederaState state, @NonNull final AccountID accountID)
            throws PreCheckException {
        final var monoAccountID = PbjConverter.fromPbj(accountID);
        final var payerNum = EntityNum.fromAccountId(monoAccountID);
        final var monoPayerStatus = delegate.payerAccountStatus(payerNum);
        final var payerStatus = PbjConverter.toPbj(monoPayerStatus);
        if (payerStatus != OK) {
            throw new PreCheckException(payerStatus);
        }
    }

    @Override
    public void checkSolvencyOfVerifiedPayer(@NonNull final HederaState state, @NonNull final Transaction transaction)
            throws InsufficientBalanceException {
        final var accessor = SignedTxnAccessor.uncheckedFrom(transaction);
        final var solvencySummary = delegate.solvencyOfVerifiedPayer(accessor, false);
        final var validity = PbjConverter.toPbj(solvencySummary.getValidity());
        if (validity != OK) {
            throw new InsufficientBalanceException(validity, solvencySummary.getRequiredFee());
        }

    }
}
