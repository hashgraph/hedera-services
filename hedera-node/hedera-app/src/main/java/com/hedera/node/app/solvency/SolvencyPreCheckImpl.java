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

package com.hedera.node.app.solvency;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A modular implementation of {@link SolvencyPreCheck}. TBD
 */
@Singleton
public class SolvencyPreCheckImpl implements SolvencyPreCheck {

    @Inject
    public SolvencyPreCheckImpl() {
        // For dagger
    }

    @Override
    public void checkPayerAccountStatus(@NonNull HederaState state, @NonNull AccountID accountID)
            throws PreCheckException {
        // TBD
    }

    @Override
    public void checkSolvencyOfVerifiedPayer(@NonNull HederaState state, @NonNull Transaction transaction)
            throws InsufficientBalanceException {
        // TBD
    }

    @Override
    public void assessWithSvcFees(@NonNull Transaction transaction) throws PreCheckException {
        // TBD
    }
}
