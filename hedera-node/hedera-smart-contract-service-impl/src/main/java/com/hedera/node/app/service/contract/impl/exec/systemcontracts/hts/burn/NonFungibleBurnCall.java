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
import static com.hedera.node.app.service.contract.impl.exec.gas.DispatchType.BURN_NFT;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.TupleType;
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
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class NonFungibleBurnCall extends AbstractHtsCall implements BurnCall {

    private final List<Long> serialNo;
    private final TokenID tokenId;
    private final TupleType outputs;
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final AccountID senderId;
    private final org.hyperledger.besu.datatypes.Address sender;

    // too may parameters
    @SuppressWarnings("java:S107")
    public NonFungibleBurnCall(
            final List<Long> serialNo,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final TokenID tokenId,
            @NonNull final TupleType outputs,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID senderId,
            @NonNull final Address sender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(gasCalculator, enhancement);
        this.tokenId = requireNonNull(tokenId);
        this.outputs = requireNonNull(outputs);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.sender = requireNonNull(sender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.serialNo = serialNo;
        this.senderId = senderId;
    }

    @Override
    public @NonNull PricedResult execute() {
        return executeBurn(
                tokenId,
                senderId,
                outputs,
                BURN_NFT,
                addressIdConverter,
                verificationStrategy,
                gasCalculator,
                this::syntheticBurnNonFungible,
                sender,
                systemContractOperations(),
                gasRequirement -> reversionWith(INVALID_TOKEN_ID, gasRequirement));
    }

    private TransactionBody syntheticBurnNonFungible() {
        return TransactionBody.newBuilder()
                .tokenBurn(TokenBurnTransactionBody.newBuilder()
                        .token(tokenId)
                        .serialNumbers(serialNo)
                        .build())
                .build();
    }
}
