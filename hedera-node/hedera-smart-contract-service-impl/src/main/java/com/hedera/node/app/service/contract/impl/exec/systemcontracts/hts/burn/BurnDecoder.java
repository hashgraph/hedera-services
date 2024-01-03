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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.INT64_INT64;

import com.esaulpaugh.headlong.abi.TupleType;
import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BurnDecoder {
    private static final TupleType BURN_RESULT_ENCODER = TupleType.parse(INT64_INT64);
    public static final DispatchForResponseCodeHtsCall.OutputFn BURN_OUTPUT_FN =
            recordBuilder -> BURN_RESULT_ENCODER.encodeElements(
                    (long) recordBuilder.status().protoOrdinal(), recordBuilder.getNewTotalSupply());

    @Inject
    public BurnDecoder() {
        // Dagger2
    }

    public TransactionBody decodeBurn(@NonNull final HtsCallAttempt attempt) {
        final var call = BurnTranslator.BURN_TOKEN_V1.decodeCall(attempt.inputBytes());
        final var amount = ((BigInteger) call.get(1)).longValueExact();
        final var serialNo = Longs.asList(call.get(2));
        return TransactionBody.newBuilder()
                .tokenBurn(burn(ConversionUtils.asTokenId(call.get(0)), amount, serialNo))
                .build();
    }

    public TransactionBody decodeBurnV2(@NonNull final HtsCallAttempt attempt) {
        final var call = BurnTranslator.BURN_TOKEN_V2.decodeCall(attempt.inputBytes());
        final long amount = call.get(1);
        final var serialNo = Longs.asList(call.get(2));
        return TransactionBody.newBuilder()
                .tokenBurn(burn(ConversionUtils.asTokenId(call.get(0)), amount, serialNo))
                .build();
    }

    private TokenBurnTransactionBody burn(
            @NonNull final TokenID tokenID, final long amount, final List<Long> serialNo) {
        return TokenBurnTransactionBody.newBuilder()
                .token(tokenID)
                .amount(amount)
                .serialNumbers(serialNo)
                .build();
    }
}
