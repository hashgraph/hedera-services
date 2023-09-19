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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
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
        return bodyOf(approveAllAllowanceNFTBody(
                attempt.addressIdConverter(), attempt.senderId(), call.get(0), call.get(1), call.get(2)));
    }

    private CryptoApproveAllowanceTransactionBody approveAllAllowanceNFTBody(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final AccountID senderId,
            @NonNull final Address tokenAddress,
            @NonNull final Address operatorAddress,
            final boolean approved) {
        return CryptoApproveAllowanceTransactionBody.newBuilder()
                .nftAllowances(nftAllowance(addressIdConverter, senderId, tokenAddress, operatorAddress, approved))
                .build();
    }

    private NftAllowance nftAllowance(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final AccountID senderId,
            @NonNull final Address tokenAddress,
            @NonNull final Address operatorAddress,
            final boolean approved) {
        return NftAllowance.newBuilder()
                .tokenId(asTokenId(tokenAddress))
                .owner(senderId)
                .spender(addressIdConverter.convert(operatorAddress))
                .approvedForAll(approved)
                .build();
    }

    private TransactionBody bodyOf(
            @NonNull final CryptoApproveAllowanceTransactionBody approveAllowanceTransactionBody) {
        return TransactionBody.newBuilder()
                .cryptoApproveAllowance(approveAllowanceTransactionBody)
                .build();
    }
}
