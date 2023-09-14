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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implements the token redirect {@code freeze()} call of the HTS system contract.
 */
public class FreezeCall extends AbstractHtsCall {
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final Address token;
    private final Address account;
    private final AccountID spender;

    // too many parameters
    protected FreezeCall(
            @NonNull final Enhancement enhancement,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID spender,
            @NonNull final Address token,
            @NonNull final Address account) {
        super(enhancement);
        this.addressIdConverter = addressIdConverter;
        this.verificationStrategy = verificationStrategy;
        this.spender = spender;
        this.token = requireNonNull(token);
        this.account = requireNonNull(account);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PricedResult execute() {
        // TODO - gas calculation
        final var freezeTransactionBody = TokenFreezeAccountTransactionBody.newBuilder()
                .account(addressIdConverter.convert(account))
                .token(ConversionUtils.asTokenId(token))
                .build();
        final var transactionBody =
                TransactionBody.newBuilder().tokenFreeze(freezeTransactionBody).build();
        final var recordBuilder = systemContractOperations()
                .dispatch(transactionBody, verificationStrategy, spender, SingleTransactionRecordBuilder.class);
        return completionWith(recordBuilder.status(), 0L);
    }
}
