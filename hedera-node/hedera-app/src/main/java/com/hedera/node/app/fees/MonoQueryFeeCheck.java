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

package com.hedera.node.app.fees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import java.util.List;
import javax.inject.Inject;

/**
 * Implementation of {@link QueryFeeCheck} that is based on the mono-service implementation.
 */
public class MonoQueryFeeCheck implements QueryFeeCheck {

    private final com.hedera.node.app.service.mono.queries.validation.QueryFeeCheck delegate;

    @Inject
    public MonoQueryFeeCheck(
            com.hedera.node.app.service.mono.queries.validation.QueryFeeCheck delegate) {
        this.delegate = delegate;
    }

    @Override
    public void validateQueryPaymentTransfers(TransactionBody txBody, long queryFee) throws InsufficientBalanceException {
        final var monoTxBody = PbjConverter.fromPbj(txBody);
        final var monoResult = delegate.validateQueryPaymentTransfers(monoTxBody);
        final var result = PbjConverter.toPbj(monoResult);
        if (result != OK) {
            throw new InsufficientBalanceException(result, queryFee);
        }
    }

    @Override
    public void nodePaymentValidity(List<AccountAmount> transfers, long queryFee, AccountID node)
            throws InsufficientBalanceException {
        final var monoNode = PbjConverter.fromPbj(node);
        final var monoTransfers = transfers.stream().map(PbjConverter::fromPbj).toList();
        final var monoResult = delegate.nodePaymentValidity(monoTransfers, queryFee, monoNode);
        final var result = PbjConverter.toPbj(monoResult);
        if (result != OK) {
            throw new InsufficientBalanceException(result, queryFee);
        }
    }
}
