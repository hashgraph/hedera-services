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

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GrantApprovalDecoder {

    @Inject
    public GrantApprovalDecoder() {
        // Dagger2
    }

    public TransactionBody decodeGrantApproval(@NonNull final HtsCallAttempt attempt) {
        final var call = GrantApprovalTranslator.GRANT_APPROVAL.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(attempt.senderId()).build())
                .cryptoApproveAllowance(
                        grantApproval(attempt.addressIdConverter(), call.get(0), call.get(1), call.get(2)))
                .build();
    }

    public TransactionBody decodeGrantApprovalNFT(@NonNull final HtsCallAttempt attempt) {
        final var call = GrantApprovalTranslator.GRANT_APPROVAL_NFT.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(attempt.senderId()).build())
                .cryptoApproveAllowance(
                        grantApprovalNFT(attempt.addressIdConverter(), call.get(0), call.get(1), call.get(2)))
                .build();
    }

    public TransactionBody decodeErcGrantApproval(@NonNull final HtsCallAttempt attempt) {
        final var call = GrantApprovalTranslator.ERC_GRANT_APPROVAL.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(attempt.senderId()).build())
                .cryptoApproveAllowance(ERCGrantApproval(
                        attempt.addressIdConverter(),
                        requireNonNull(attempt.redirectTokenId()),
                        call.get(0),
                        call.get(1),
                        requireNonNull(attempt.redirectTokenType())))
                .build();
    }

    private CryptoApproveAllowanceTransactionBody grantApproval(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final Address token,
            @NonNull final Address spender,
            @NonNull final BigInteger amount) {
        return CryptoApproveAllowanceTransactionBody.newBuilder()
                .tokenAllowances(TokenAllowance.newBuilder()
                        .tokenId(ConversionUtils.asTokenId(token))
                        .spender(addressIdConverter.convert(spender))
                        .amount(amount.longValue())
                        .build())
                .build();
    }

    private CryptoApproveAllowanceTransactionBody ERCGrantApproval(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final TokenID token,
            @NonNull final Address spender,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        return tokenType.equals(TokenType.FUNGIBLE_COMMON)
                ? CryptoApproveAllowanceTransactionBody.newBuilder()
                        .tokenAllowances(TokenAllowance.newBuilder()
                                .tokenId(token)
                                .spender(addressIdConverter.convert(spender))
                                .amount(amount.longValue())
                                .build())
                        .build()
                : CryptoApproveAllowanceTransactionBody.newBuilder()
                        .nftAllowances(NftAllowance.newBuilder()
                                .tokenId(token)
                                .spender(addressIdConverter.convert(spender))
                                .serialNumbers(amount.longValue())
                                .build())
                        .build();
    }

    private CryptoApproveAllowanceTransactionBody grantApprovalNFT(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final Address token,
            @NonNull final Address spender,
            @NonNull final BigInteger serialNumber) {
        return CryptoApproveAllowanceTransactionBody.newBuilder()
                .nftAllowances(NftAllowance.newBuilder()
                        .tokenId(ConversionUtils.asTokenId(token))
                        .spender(addressIdConverter.convert(spender))
                        .serialNumbers(serialNumber.longValue())
                        .build())
                .build();
    }
}
