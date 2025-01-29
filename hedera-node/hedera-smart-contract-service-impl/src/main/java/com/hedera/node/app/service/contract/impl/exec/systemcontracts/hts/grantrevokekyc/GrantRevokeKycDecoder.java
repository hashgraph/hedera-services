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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides help in decoding an {@link HtsCallAttempt} representing a grantKyc or revokeKyc call into
 * a synthetic {@link TransactionBody}.
 */
@Singleton
public class GrantRevokeKycDecoder {

    /**
     * Default constructor for injection.
     */
    @Inject
    public GrantRevokeKycDecoder() {
        // Dagger2
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a grantKyc function call.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeGrantKyc(@NonNull final HtsCallAttempt attempt) {
        final var call = GrantRevokeKycTranslator.GRANT_KYC.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenGrantKyc(grantKyc(call.get(0), call.get(1), attempt.addressIdConverter()))
                .build();
    }

    private TokenGrantKycTransactionBody grantKyc(
            @NonNull final Address tokenAddress,
            @NonNull final Address accountAddress,
            @NonNull final AddressIdConverter addressIdConverter) {
        return TokenGrantKycTransactionBody.newBuilder()
                .token(asTokenId(tokenAddress))
                .account(addressIdConverter.convert(accountAddress))
                .build();
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a revokeKyc function call.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeRevokeKyc(@NonNull final HtsCallAttempt attempt) {
        final var call = GrantRevokeKycTranslator.REVOKE_KYC.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenRevokeKyc(revokeKyc(call.get(0), call.get(1), attempt.addressIdConverter()))
                .build();
    }

    private TokenRevokeKycTransactionBody revokeKyc(
            @NonNull final Address tokenAddress,
            @NonNull final Address accountAddress,
            @NonNull final AddressIdConverter addressIdConverter) {
        return TokenRevokeKycTransactionBody.newBuilder()
                .token(asTokenId(tokenAddress))
                .account(addressIdConverter.convert(accountAddress))
                .build();
    }
}
