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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code approve}, {@code approveNFT} calls to the HTS system contract.
 */
@Singleton
public class GrantApprovalTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    /** Selector for approve(address,uint256) method. */
    public static final Function ERC_GRANT_APPROVAL = new Function("approve(address,uint256)", ReturnTypes.BOOL);
    /** Selector for approve(address,uint256) method. */
    public static final Function ERC_GRANT_APPROVAL_NFT = new Function("approve(address,uint256)");
    /** Selector for approve(address,address,uint256) method. */
    public static final Function GRANT_APPROVAL = new Function("approve(address,address,uint256)", "(int32,bool)");
    /** Selector for approveNFT(address,address,uint256) method. */
    public static final Function GRANT_APPROVAL_NFT =
            new Function("approveNFT(address,address,uint256)", ReturnTypes.INT_64);

    private final GrantApprovalDecoder decoder;

    /**
     * Constructor for injection.
     * @param decoder the decoder used to decode transfer calls
     */
    @Inject
    public GrantApprovalTranslator(@NonNull final GrantApprovalDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(GRANT_APPROVAL, GRANT_APPROVAL_NFT) || attempt.isSelector(ERC_GRANT_APPROVAL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(ERC_GRANT_APPROVAL)) {
            return bodyForErc(attempt);
        } else if (attempt.isSelector(GRANT_APPROVAL, GRANT_APPROVAL_NFT)) {
            return bodyForClassicCall(attempt);
        } else {
            return new DispatchForResponseCodeHtsCall(
                    attempt, bodyForClassic(attempt), GrantApprovalTranslator::gasRequirement);
        }
    }

    /**
     * @param body                          the transaction body to be dispatched
     * @param systemContractGasCalculator   the gas calculator for the system contract
     * @param enhancement                   the enhancement to use
     * @param payerId                       the payer of the transaction
     * @return the required gas
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.APPROVE, payerId);
    }

    private TransactionBody bodyForClassic(final HtsCallAttempt attempt) {
        if (attempt.isSelector(GRANT_APPROVAL)) {
            return decoder.decodeGrantApproval(attempt);
        } else {
            return decoder.decodeGrantApprovalNFT(attempt);
        }
    }

    private ClassicGrantApprovalCall bodyForClassicCall(final HtsCallAttempt attempt) {
        final var isFungibleCall = attempt.isSelector(GRANT_APPROVAL);
        final var tokenType = isFungibleCall ? TokenType.FUNGIBLE_COMMON : TokenType.NON_FUNGIBLE_UNIQUE;
        final var call = isFungibleCall
                ? GrantApprovalTranslator.GRANT_APPROVAL.decodeCall(attempt.inputBytes())
                : GrantApprovalTranslator.GRANT_APPROVAL_NFT.decodeCall(attempt.inputBytes());
        final var tokenAddress = call.get(0);
        final var spender = attempt.addressIdConverter().convert(call.get(1));
        final var amount = exactLongFrom(call.get(2));
        return new ClassicGrantApprovalCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.defaultVerificationStrategy(),
                attempt.senderId(),
                ConversionUtils.asTokenId((Address) tokenAddress),
                spender,
                amount,
                tokenType);
    }

    private ERCGrantApprovalCall bodyForErc(final HtsCallAttempt attempt) {
        final var call = GrantApprovalTranslator.ERC_GRANT_APPROVAL.decodeCall(attempt.inputBytes());
        final var spenderId = attempt.addressIdConverter().convert(call.get(0));
        final var amount = exactLongFrom(call.get(1));
        return new ERCGrantApprovalCall(
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.defaultVerificationStrategy(),
                attempt.senderId(),
                requireNonNull(attempt.redirectTokenId()),
                spenderId,
                amount,
                requireNonNull(attempt.redirectTokenType()));
    }

    private long exactLongFrom(@NonNull final BigInteger value) {
        requireNonNull(value);
        return value.longValueExact();
    }
}
