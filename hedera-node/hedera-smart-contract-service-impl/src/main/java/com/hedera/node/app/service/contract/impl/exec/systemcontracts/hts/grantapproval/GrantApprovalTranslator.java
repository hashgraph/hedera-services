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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code approve}, {@code approveNFT} calls to the HTS system contract.
 */
@Singleton
public class GrantApprovalTranslator extends AbstractHtsCallTranslator {

    public static final Function ERC_GRANT_APPROVAL = new Function("approve(address,uint256)", ReturnTypes.BOOL);
    public static final Function ERC_GRANT_APPROVAL_NFT = new Function("approve(address,uint256)");
    public static final Function GRANT_APPROVAL = new Function("approve(address,address,uint256)", ReturnTypes.INT_64);
    public static final Function GRANT_APPROVAL_NFT =
            new Function("approveNFT(address,address,uint256)", ReturnTypes.INT_64);
    private final GrantApprovalDecoder decoder;

    @Inject
    public GrantApprovalTranslator(@NonNull final GrantApprovalDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return matchesClassicSelector(attempt.selector()) || matchesErcSelector(attempt.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        if (matchesErcSelector(attempt.selector())) {
            return bodyForErc(attempt);
        } else if (matchesClassicSelector(attempt.selector())) {
            return bodyForClassicCall(attempt);
        } else {
            return new DispatchForResponseCodeHtsCall<>(
                    attempt, bodyForClassic(attempt), SingleTransactionRecordBuilder.class);
        }
    }

    private boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, GRANT_APPROVAL.selector())
                || Arrays.equals(selector, GRANT_APPROVAL_NFT.selector());
    }

    private boolean matchesErcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_GRANT_APPROVAL.selector());
    }

    private TransactionBody bodyForClassic(final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), GRANT_APPROVAL.selector())) {
            return decoder.decodeGrantApproval(attempt);
        } else {
            return decoder.decodeGrantApprovalNFT(attempt);
        }
    }

    private ClassicGrantApprovalCall bodyForClassicCall(final HtsCallAttempt attempt) {
        final var tokenType = Arrays.equals(attempt.selector(), GRANT_APPROVAL.selector())
                ? TokenType.FUNGIBLE_COMMON
                : TokenType.NON_FUNGIBLE_UNIQUE;
        final var call = Arrays.equals(attempt.selector(), GRANT_APPROVAL.selector())
                ? GrantApprovalTranslator.GRANT_APPROVAL.decodeCall(attempt.inputBytes())
                : GrantApprovalTranslator.GRANT_APPROVAL_NFT.decodeCall(attempt.inputBytes());
        final var tokenAddress = call.get(0);
        final var spender = attempt.addressIdConverter().convert(call.get(1));
        final var amount = call.get(2);
        return new ClassicGrantApprovalCall(
                attempt.enhancement(),
                attempt.defaultVerificationStrategy(),
                attempt.senderId(),
                ConversionUtils.asTokenId((Address) tokenAddress),
                spender,
                (BigInteger) amount,
                tokenType);
    }

    private ERCGrantApprovalCall bodyForErc(final HtsCallAttempt attempt) {
        final var call = GrantApprovalTranslator.ERC_GRANT_APPROVAL.decodeCall(attempt.inputBytes());
        final var spender = attempt.addressIdConverter().convert(call.get(0));
        final var amount = call.get(1);
        return new ERCGrantApprovalCall(
                attempt.enhancement(),
                attempt.defaultVerificationStrategy(),
                attempt.senderId(),
                Objects.requireNonNull(attempt.redirectTokenId()),
                spender,
                (BigInteger) amount,
                Objects.requireNonNull(attempt.redirectTokenType()));
    }
}
