/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetAllowanceCall extends AbstractHtsCall {

    private final Address owner;
    private final Address spender;
    private final AddressIdConverter addressIdConverter;
    private final boolean isERCCall;
    private final boolean isStaticCall;

    @Nullable
    private final Token token;

    @Inject
    public GetAllowanceCall(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            @NonNull final Address owner,
            @NonNull final Address spender,
            final boolean isERCCall,
            final boolean isStaticCall) {
        super(gasCalculator, enhancement, true);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.token = token;
        this.owner = requireNonNull(owner);
        this.spender = requireNonNull(spender);
        this.isERCCall = isERCCall;
        this.isStaticCall = isStaticCall;
    }

    @Override
    public boolean allowsStaticFrame() {
        return true;
    }

    @Override
    public @NonNull PricedResult execute() {
        final var gasRequirement = gasCalculator.viewGasRequirement();
        if (token == null || token.tokenType() != FUNGIBLE_COMMON) {
            if (isStaticCall) {
                return gasOnly(revertResult(INVALID_TOKEN_ID, gasRequirement), INVALID_TOKEN_ID, false);
            } else {
                return gasOnly(successResult(encodedAllowanceOutput(BigInteger.ZERO), gasRequirement), SUCCESS, false);
            }
        }

        final var ownerId = addressIdConverter.convert(owner);
        final var ownerAccount = nativeOperations().getAccount(ownerId);
        if (ownerAccount == null) {
            return gasOnly(revertResult(INVALID_ALLOWANCE_OWNER_ID, gasRequirement), INVALID_ALLOWANCE_OWNER_ID, true);
        } else {
            final var spenderId = addressIdConverter.convert(spender);
            final var allowance = getAllowance(token, ownerAccount, spenderId);
            return gasOnly(successResult(encodedAllowanceOutput(allowance), gasRequirement), SUCCESS, true);
        }
    }

    @NonNull
    private BigInteger getAllowance(
            @NonNull final Token token, @NonNull final Account ownerAccount, @NonNull final AccountID spenderID) {
        final var tokenAllowance = requireNonNull(ownerAccount).tokenAllowancesOrThrow().stream()
                .filter(allowance -> allowance.tokenIdOrThrow().equals(token.tokenIdOrThrow())
                        && allowance.spenderIdOrThrow().equals(spenderID))
                .findFirst();
        return BigInteger.valueOf(
                tokenAllowance.map(AccountFungibleTokenAllowance::amount).orElse(0L));
    }

    @NonNull
    private ByteBuffer encodedAllowanceOutput(@NonNull final BigInteger allowance) {
        if (isERCCall) {
            return GetAllowanceTranslator.ERC_GET_ALLOWANCE.getOutputs().encodeElements(allowance);
        } else {
            return GetAllowanceTranslator.GET_ALLOWANCE
                    .getOutputs()
                    .encodeElements((long) SUCCESS.protoOrdinal(), allowance);
        }
    }
}
