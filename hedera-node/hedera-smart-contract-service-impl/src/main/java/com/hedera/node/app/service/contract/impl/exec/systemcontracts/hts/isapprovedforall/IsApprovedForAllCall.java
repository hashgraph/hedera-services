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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.accountNumberForEvmReference;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Implements the token redirect {@code isApprovedForAll()} call of the HTS system contract.
 */
public class IsApprovedForAllCall extends AbstractRevertibleTokenViewCall {
    public static final Function IS_APPROVED_FOR_ALL =
            new Function("isApprovedForAll(address,address)", ReturnTypes.BOOL);

    private final Address owner;
    private final Address operator;

    public IsApprovedForAllCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            @NonNull final Address owner,
            @NonNull final Address operator) {
        super(enhancement, token);
        this.owner = requireNonNull(owner);
        this.operator = requireNonNull(operator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        // TODO - gas calculation
        if (token.tokenType() != TokenType.NON_FUNGIBLE_UNIQUE) {
            return revertResult(INVALID_TOKEN_ID, 0L);
        }
        final var ownerNum = accountNumberForEvmReference(owner, nativeOperations());
        if (ownerNum < 0) {
            return revertResult(INVALID_ACCOUNT_ID, 0L);
        }
        final var operatorNum = accountNumberForEvmReference(operator, nativeOperations());
        final boolean verdict;
        if (operatorNum < 0) {
            verdict = false;
        } else {
            verdict = operatorMatches(
                    requireNonNull(nativeOperations().getAccount(ownerNum)),
                    AccountID.newBuilder().accountNum(operatorNum).build(),
                    token.tokenIdOrThrow());
        }
        return successResult(IS_APPROVED_FOR_ALL.getOutputs().encodeElements(verdict), 0L);
    }

    private boolean operatorMatches(
            @NonNull final Account owner, @NonNull final AccountID operatorId, @NonNull final TokenID tokenId) {
        final var operatorApprovals =
                Optional.ofNullable(owner.approveForAllNftAllowances()).orElse(emptyList());
        for (final var approval : operatorApprovals) {
            if (tokenId.equals(approval.tokenIdOrThrow()) && operatorId.equals(approval.spenderIdOrThrow())) {
                return true;
            }
        }
        return false;
    }
}
