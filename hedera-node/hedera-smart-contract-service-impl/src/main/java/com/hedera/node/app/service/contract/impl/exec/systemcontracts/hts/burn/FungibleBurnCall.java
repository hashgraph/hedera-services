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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.gas.DispatchType.BURN_FUNGIBLE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class FungibleBurnCall extends AbstractHtsCall implements BurnCall {

    private final long amount;

    @Nullable
    private final TokenID tokenId;

    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final AccountID senderId;
    private final org.hyperledger.besu.datatypes.Address sender;

    // too may parameters
    @SuppressWarnings("java:S107")
    public FungibleBurnCall(
            final long amount,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID senderId,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(gasCalculator, enhancement);
        this.amount = amount;
        this.tokenId = tokenId;
        this.sender = requireNonNull(sender);
        this.senderId = requireNonNull(senderId);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.addressIdConverter = requireNonNull(addressIdConverter);
    }

    @Override
    public @NonNull PricedResult execute() {
        return executeBurn(
                tokenId,
                senderId,
                BURN_FUNGIBLE,
                addressIdConverter,
                verificationStrategy,
                gasCalculator,
                () -> syntheticBurnUnits(requireNonNull(tokenId), amount),
                sender,
                systemContractOperations(),
                gasRequirement -> reversionWith(INVALID_TOKEN_ID, gasRequirement));
    }

    private TransactionBody syntheticBurnUnits(@NonNull final TokenID tokenId, final long amount) {
        return TransactionBody.newBuilder()
                .tokenBurn(TokenBurnTransactionBody.newBuilder()
                        .token(tokenId)
                        .amount(amount)
                        .build())
                .build();
    }
}
