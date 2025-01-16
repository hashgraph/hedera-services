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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FreezeUnfreezeDecoder {

    @Inject
    public FreezeUnfreezeDecoder() {
        // Dagger2
    }

    public TransactionBody decodeFreeze(@NonNull final HtsCallAttempt attempt) {
        final var call = FreezeUnfreezeTranslator.FREEZE.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenFreeze(freeze(attempt.addressIdConverter(), call.get(0), call.get(1)))
                .build();
    }

    public TransactionBody decodeUnfreeze(@NonNull final HtsCallAttempt attempt) {
        final var call = FreezeUnfreezeTranslator.UNFREEZE.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenUnfreeze(unfreeze(attempt.addressIdConverter(), call.get(0), call.get(1)))
                .build();
    }

    private TokenFreezeAccountTransactionBody freeze(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final Address token,
            @NonNull final Address account) {
        return TokenFreezeAccountTransactionBody.newBuilder()
                .account(addressIdConverter.convert(account))
                .token(ConversionUtils.asTokenId(token))
                .build();
    }

    private TokenUnfreezeAccountTransactionBody unfreeze(
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final Address token,
            @NonNull final Address account) {
        return TokenUnfreezeAccountTransactionBody.newBuilder()
                .account(addressIdConverter.convert(account))
                .token(ConversionUtils.asTokenId(token))
                .build();
    }
}
