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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;

public class BurnTranslator extends AbstractHtsCallTranslator {

    public static final Function BURN_TOKEN_V1 =
            new Function("burnToken(address,uint64,int64[])", ReturnTypes.INT64_INT64);
    public static final Function BURN_TOKEN_V2 =
            new Function("burnToken(address,int64,int64[])", ReturnTypes.INT64_INT64);

    @Inject
    public BurnTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V1.selector())
                || Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V2.selector());
    }

    @Override
    public BurnCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        final Tuple call;
        final long amount;
        final TupleType outputs;
        final var isV1Call = Arrays.equals(selector, BurnTranslator.BURN_TOKEN_V1.selector());
        if (isV1Call) {
            call = BurnTranslator.BURN_TOKEN_V1.decodeCall(attempt.input().toArrayUnsafe());
            amount = ((BigInteger) call.get(1)).longValueExact();
            outputs = BurnTranslator.BURN_TOKEN_V1.getOutputs();
        } else {
            call = BurnTranslator.BURN_TOKEN_V2.decodeCall(attempt.input().toArrayUnsafe());
            amount = call.get(1);
            outputs = BurnTranslator.BURN_TOKEN_V2.getOutputs();
        }
        final var token = attempt.linkedToken(Address.fromHexString(call.get(0).toString()));
        if (token == null) {
            return null;
        } else {
            return token.tokenType() == TokenType.FUNGIBLE_COMMON
                    ? new FungibleBurnCall(
                            amount,
                            attempt.systemContractGasCalculator(),
                            attempt.enhancement(),
                            ConversionUtils.asTokenId(call.get(0)),
                            outputs,
                            attempt.defaultVerificationStrategy(),
                            attempt.senderId(),
                            attempt.senderAddress(),
                            attempt.addressIdConverter())
                    : new NonFungibleBurnCall(
                            Longs.asList(call.get(2)),
                            attempt.systemContractGasCalculator(),
                            attempt.enhancement(),
                            ConversionUtils.asTokenId(call.get(0)),
                            outputs,
                            attempt.defaultVerificationStrategy(),
                            attempt.senderId(),
                            attempt.senderAddress(),
                            attempt.addressIdConverter());
        }
    }
}
