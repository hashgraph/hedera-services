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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MintDecoder {
    private static final TupleType MINT_RESULT_ENCODER = TupleType.parse("(int64,int64,int64[])");
    public static final DispatchForResponseCodeHtsCall.OutputFn MINT_OUTPUT_FN =
            recordBuilder -> MINT_RESULT_ENCODER.encodeElements(
                    (long) recordBuilder.status().protoOrdinal(),
                    recordBuilder.getNewTotalSupply(),
                    recordBuilder.serialNumbers().stream()
                            .mapToLong(Long::longValue)
                            .toArray());

    @Inject
    public MintDecoder() {
        // Dagger2
    }

    public TransactionBody decodeMint(@NonNull final HtsCallAttempt attempt) {
        final var call = MintTranslator.MINT.decodeCall(attempt.inputBytes());
        final var amount = ((BigInteger) call.get(1)).longValueExact();
        return TransactionBody.newBuilder()
                .tokenMint(mint(call.get(0), amount, call.get(2)))
                .build();
    }

    public TransactionBody decodeMintV2(@NonNull final HtsCallAttempt attempt) {
        final var call = MintTranslator.MINT_V2.decodeCall(attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenMint(mint(call.get(0), call.get(1), call.get(2)))
                .build();
    }

    private TokenMintTransactionBody mint(
            @NonNull final Address token, final long amount, @NonNull final byte[][] metadataArray) {
        final List<Bytes> metadata = new ArrayList<>();
        for (final var data : metadataArray) {
            metadata.add(Bytes.wrap(data));
        }

        return TokenMintTransactionBody.newBuilder()
                .token(ConversionUtils.asTokenId(token))
                .amount(amount)
                .metadata(metadata)
                .build();
    }
}
