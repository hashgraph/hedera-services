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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractTokenViewCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetAllowanceCall extends AbstractTokenViewCall {

    private final Address owner;
    private final Address spender;
    private final AddressIdConverter addressIdConverter;

    @Inject
    public GetAllowanceCall(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            @NonNull final Address owner,
            @NonNull final Address spender) {
        super(enhancement, token);
        this.addressIdConverter = addressIdConverter;
        this.owner = requireNonNull(owner);
        this.spender = requireNonNull(spender);
    }

    @NonNull
    @Override
    protected FullResult resultOfViewingToken(@NonNull final Token token) {
        // TODO: gas calculation
        requireNonNull(token);
        requireNonNull(owner);
        requireNonNull(spender);
        if (token.tokenType() != TokenType.FUNGIBLE_COMMON) {
            return FullResult.revertResult(com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID, 0L);
        }
        final var ownerID = addressIdConverter.convert(owner);
        final var ownerAccount = nativeOperations().getAccount(ownerID.accountNumOrThrow());
        final var spenderID = addressIdConverter.convert(spender);
        final var allowance = getAllowance(token, requireNonNull(ownerAccount), spenderID);
        final var output = GetAllowanceTranslator.GET_ALLOWANCE
                .getOutputs()
                .encodeElements((long) ResponseCodeEnum.SUCCESS.getNumber(), allowance);
        return FullResult.successResult(output, 0L);
    }

    @NonNull
    private static BigInteger getAllowance(
            @NonNull final Token token, @NonNull final Account ownerAccount, @NonNull final AccountID spenderID) {
        final var tokenAllowance = requireNonNull(ownerAccount).tokenAllowancesOrThrow().stream()
                .filter(allowance -> allowance.tokenIdOrThrow().equals(token.tokenIdOrThrow())
                        && allowance.spenderIdOrThrow().equals(spenderID))
                .findFirst();
        return BigInteger.valueOf(
                tokenAllowance.map(AccountFungibleTokenAllowance::amount).orElse(0L));
    }
}
