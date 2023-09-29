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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

public class CreateTranslator extends AbstractHtsCallTranslator {

    public static final Function CREATE_FUNGIBLE_TOKEN =
            new Function("createFungibleToken(" + HEDERA_TOKEN + ",int64,int32)", ReturnTypes.INT);

    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN
                    + ",int64,int32,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);

    public static final Function CREATE_NON_FUNGIBLE_TOKEN =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_STRUCT + ")", ReturnTypes.INT);

    private final CreateDecoder decoder;

    @Inject
    public CreateTranslator(CreateDecoder decoder) {
        // Dagger2
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN.selector());
    }

    @Override
    public FungibleCreatesCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new FungibleCreatesCall(
                attempt.enhancement(),
                nominalBodyFor(attempt),
                attempt.defaultVerificationStrategy(),
                attempt.senderAddress(),
                attempt.addressIdConverter());
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN.selector())) {
            return decoder.decodeCreateFungibleToken(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES.selector())) {
            return decoder.decodeCreateFungibleTokenWithCustomFees(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN.selector())) {
            return decoder.decodeCreateFungibleTokenWithCustomFees(attempt.inputBytes(), attempt.addressIdConverter());
        }
        throw new AssertionError("Add more cases");
    }
}
