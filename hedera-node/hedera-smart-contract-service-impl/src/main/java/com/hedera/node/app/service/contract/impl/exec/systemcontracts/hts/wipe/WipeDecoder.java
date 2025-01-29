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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides help in decoding an {@link HtsCallAttempt} representing an pause or unpause call into a synthetic
 * {@link TransactionBody}.
 */
@Singleton
public class WipeDecoder {
    @Inject
    public WipeDecoder() {
        // Dagger2
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a wipeV1 function call for Fungible Token.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeWipeFungibleV1(@NonNull final HtsCallAttempt attempt) {
        final var call = WipeTranslator.WIPE_FUNGIBLE_V1.decodeCall(attempt.inputBytes());
        return bodyOf(wipeFungible(attempt.addressIdConverter(), call.get(0), call.get(1), call.get(2)));
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a wipeV2 function call for Fungible Token.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeWipeFungibleV2(@NonNull final HtsCallAttempt attempt) {
        final var call = WipeTranslator.WIPE_FUNGIBLE_V2.decodeCall(attempt.inputBytes());
        return bodyOf(wipeFungible(attempt.addressIdConverter(), call.get(0), call.get(1), call.get(2)));
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody}for a wipe function call for Non-Fungible Token.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeWipeNonFungible(@NonNull final HtsCallAttempt attempt) {
        final var call = WipeTranslator.WIPE_NFT.decodeCall(attempt.inputBytes());
        return bodyOf(wipeNonFungible(attempt.addressIdConverter(), call.get(0), call.get(1), call.get(2)));
    }

    private TokenWipeAccountTransactionBody wipeFungible(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final Address tokenAddress,
            @NonNull final Address accountAddress,
            final long amount) {
        return TokenWipeAccountTransactionBody.newBuilder()
                .token(asTokenId(tokenAddress))
                .account(addressIdConverter.convert(accountAddress))
                .amount(amount)
                .build();
    }

    private TokenWipeAccountTransactionBody wipeNonFungible(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final Address tokenAddress,
            @NonNull final Address accountAddress,
            @NonNull final long[] serialNumbers) {
        return TokenWipeAccountTransactionBody.newBuilder()
                .token(asTokenId(tokenAddress))
                .account(addressIdConverter.convert(accountAddress))
                .serialNumbers(Arrays.stream(serialNumbers).boxed().toList())
                .build();
    }

    private TransactionBody bodyOf(@NonNull final TokenWipeAccountTransactionBody wipeAccountTransactionBody) {
        return TransactionBody.newBuilder()
                .tokenWipe(wipeAccountTransactionBody)
                .build();
    }
}
