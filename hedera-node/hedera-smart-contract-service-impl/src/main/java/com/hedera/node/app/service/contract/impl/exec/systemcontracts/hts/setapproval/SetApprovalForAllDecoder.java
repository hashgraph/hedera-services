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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SetApprovalForAllDecoder {

    @Inject
    public SetApprovalForAllDecoder() {
        // Dagger 2 constructor
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a setApprovalForAll function call.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeSetApprovalForAll(@NonNull final HtsCallAttempt attempt) {
        final var call = SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL.decodeCall(attempt.inputBytes());
        final var operatorId = attempt.addressIdConverter().convert(call.get(1));
        return bodyOf(approveAllAllowanceNFTBody(attempt.senderId(), asTokenId(call.get(0)), operatorId, call.get(2)));
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a setApprovalForAll ERC function call.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeSetApprovalForAllERC(@NonNull final HtsCallAttempt attempt) {
        final var call = SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL.decodeCall(attempt.inputBytes());
        final var tokenId = attempt.redirectTokenId();
        Objects.requireNonNull(tokenId, "Redirect Token ID is null.");

        final var operatorId = attempt.addressIdConverter().convert(call.get(0));
        return bodyOf(approveAllAllowanceNFTBody(attempt.senderId(), tokenId, operatorId, call.get(1)));
    }

    private CryptoApproveAllowanceTransactionBody approveAllAllowanceNFTBody(
            @NonNull final AccountID senderId,
            @NonNull final TokenID tokenID,
            @NonNull final AccountID operatorId,
            final boolean approved) {
        return CryptoApproveAllowanceTransactionBody.newBuilder()
                .nftAllowances(NftAllowance.newBuilder()
                        .tokenId(tokenID)
                        .owner(senderId)
                        .spender(operatorId)
                        .approvedForAll(approved)
                        .build())
                .build();
    }

    private TransactionBody bodyOf(
            @NonNull final CryptoApproveAllowanceTransactionBody approveAllowanceTransactionBody) {
        return TransactionBody.newBuilder()
                .cryptoApproveAllowance(approveAllowanceTransactionBody)
                .build();
    }
}
