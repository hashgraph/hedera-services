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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Predicate;
import javax.inject.Inject;

/**
 * Provides the "extended" scope a Hedera system contract needs to perform its operations.
 *
 * <p>This lets an EVM smart contract make atomic changes scoped to a message frame, even though
 * these changes involve state that it cannot mutate directly via the {@code ContractService}'s
 * {@code WritableStates}.
 */
@QueryScope
public class QuerySystemContractOperations implements SystemContractOperations {

    private final QueryContext context;

    @Inject
    public QuerySystemContractOperations(@NonNull final QueryContext queryContext) {
        this.context = queryContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Nft getNftAndExternalizeResult(
            @NonNull final NftID id,
            final long callingContractNumber,
            @NonNull final ResultTranslator<Nft> translator) {
        throw new UnsupportedOperationException("Cannot get Nft and externalize result");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Token getTokenAndExternalizeResult(
            final long number, final long callingContractNumber, @NonNull final ResultTranslator<Token> translator) {
        throw new UnsupportedOperationException("Cannot get fungible token and externalize result");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Account getAccountAndExternalizeResult(
            final long number, final long callingContractNumber, @NonNull final ResultTranslator<Account> translator) {
        throw new UnsupportedOperationException("Cannot get account and externalize result");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> T dispatch(
            @NonNull final TransactionBody syntheticTransaction,
            @NonNull final VerificationStrategy strategy,
            @NonNull AccountID syntheticPayerId,
            @NonNull Class<T> recordBuilderClass) {
        throw new UnsupportedOperationException("Cannot dispatch synthetic transaction");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Predicate<Key> activeSignatureTestWith(@NonNull final VerificationStrategy strategy) {
        throw new UnsupportedOperationException("Cannot compute a signature test");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void externalizeResult(
            @NonNull final ContractFunctionResult result,
            @NonNull final ResultStatus status,
            @NonNull final ResponseCodeEnum responseStatus) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ExchangeRate currentExchangeRate() {
        return context.exchangeRateInfo().activeRate(Instant.now());
    }
}
