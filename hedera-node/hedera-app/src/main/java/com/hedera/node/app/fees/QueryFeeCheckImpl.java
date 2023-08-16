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

package com.hedera.node.app.fees;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A modular implementation of the {@link QueryFeeCheck}. TBD.
 */
@Singleton
public class QueryFeeCheckImpl implements QueryFeeCheck {

    @Inject
    public QueryFeeCheckImpl() {
        // For dagger
    }

    @Override
    public void validateQueryPaymentTransfers(@NonNull TransactionBody txBody, long queryFee)
            throws InsufficientBalanceException {
        // TBD
    }

    @Override
    public void nodePaymentValidity(@NonNull List<AccountAmount> transfers, long queryFee, @NonNull AccountID node)
            throws InsufficientBalanceException {
        // TBD
    }
}
