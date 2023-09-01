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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.Arrays;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implements the token redirect {@code balanceOf()} call of the HTS system contract.
 */
public class BalanceOfCall extends AbstractHtsCall {
    public static final Function BALANCE_OF = new Function("balanceOf(address)", ReturnTypes.INT);

    @Nullable
    private final Token token;

    private final Address owner;

    public BalanceOfCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            @NonNull final Address owner) {
        super(enhancement);
        this.owner = requireNonNull(owner);
        this.token = token;
    }

    @Override
    public @NonNull PricedResult execute() {
        // TODO - gas calculation
        if (token == null) {
            return gasOnly(revertResult(INVALID_TOKEN_ID, 0L));
        }
        final var ownerNum = maybeMissingNumberOf(owner, enhancement.nativeOperations());
        if (ownerNum == MISSING_ENTITY_NUMBER) {
            return gasOnly(revertResult(INVALID_ACCOUNT_ID, 0L));
        }

        final var tokenNum = token.tokenIdOrThrow().tokenNum();
        final var relation = enhancement.nativeOperations().getTokenRelation(ownerNum, tokenNum);
        final var balance = relation == null ? 0 : relation.balance();
        final var output = BALANCE_OF.getOutputs().encodeElements(BigInteger.valueOf(balance));

        return gasOnly(successResult(output, 0L));
    }

    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, BALANCE_OF.selector());
    }

    public static BalanceOfCall from(@NonNull final HtsCallAttempt attempt) {
        final var owner = fromHeadlongAddress(
                BALANCE_OF.decodeCall(attempt.input().toArrayUnsafe()).get(0));
        return new BalanceOfCall(attempt.enhancement(), attempt.redirectToken(), owner);
    }
}
