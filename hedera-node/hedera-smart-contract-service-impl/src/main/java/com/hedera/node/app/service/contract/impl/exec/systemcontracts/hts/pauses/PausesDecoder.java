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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.pauses;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides help in decoding an {@link HtsCallAttempt} representing an pause or unpause call into
 * a synthetic {@link TransactionBody}.
 */
@Singleton
public class PausesDecoder {
    @Inject
    public PausesDecoder() {
        // Dagger2
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for a pause function call.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodePause(@NonNull final HtsCallAttempt attempt) {
        final var call = PausesTranslator.PAUSE.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder().tokenPause(pause(call.get(0))).build();
    }

    private TokenPauseTransactionBody pause(@NonNull final Address tokenAddress) {
        return TokenPauseTransactionBody.newBuilder()
                .token(asTokenId(tokenAddress))
                .build();
    }

    /**
     * Decodes the given {@code attempt} into a {@link TransactionBody} for an unpause function call.
     *
     * @param attempt the attempt to decode
     * @return a {@link TransactionBody}
     */
    public TransactionBody decodeUnpause(@NonNull final HtsCallAttempt attempt) {
        final var call = PausesTranslator.UNPAUSE.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder().tokenUnpause(unpause(call.get(0))).build();
    }

    private TokenUnpauseTransactionBody unpause(@NonNull final Address tokenAddress) {
        return TokenUnpauseTransactionBody.newBuilder()
                .token(asTokenId(tokenAddress))
                .build();
    }
}
