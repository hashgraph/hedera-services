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
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V1;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V3;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE_V2;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

public class CreateTranslator extends AbstractHtsCallTranslator {

    public static final Function CREATE_FUNGIBLE_TOKEN_V1 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_V1 + ",uint,uint)", ReturnTypes.INT);
    public static final Function CREATE_FUNGIBLE_TOKEN_V2 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_V2 + ",uint64,uint32)", ReturnTypes.INT);
    public static final Function CREATE_FUNGIBLE_TOKEN_V3 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_V3 + ",int64,int32)", ReturnTypes.INT);
    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1 = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V1
                    + ",uint,uint,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);
    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2 = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V2
                    + ",uint64,uint32,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);
    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3 = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V3
                    + ",int64,int32,"
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE_V2
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);

    public static final Function CREATE_NON_FUNGIBLE_TOKEN_V1 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_V1 + ")", ReturnTypes.INT);
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_V2 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_V2 + ")", ReturnTypes.INT);
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_V3 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_V3 + ")", ReturnTypes.INT);

    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1 = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V1
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2 = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V2
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3 = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V3
                    + ","
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE_V2
                    + ARRAY_BRACKETS
                    + ")",
            ReturnTypes.INT);

    private final CreateDecoder decoder;

    @Inject
    public CreateTranslator(CreateDecoder decoder) {
        // Dagger2
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2.selector())
                || Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3.selector())
                || Arrays.equals(
                        attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1.selector())
                || Arrays.equals(
                        attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2.selector())
                || Arrays.equals(
                        attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3.selector());
    }

    @Override
    public ClassicCreatesCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new ClassicCreatesCall(
                attempt.enhancement(),
                nominalBodyFor(attempt),
                attempt.defaultVerificationStrategy(),
                attempt.senderAddress(),
                attempt.addressIdConverter());
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        final var inputBytes = attempt.inputBytes();
        final var senderId = attempt.senderId();
        final var nativeOperations = attempt.nativeOperations();
        final var addressIdConverter = attempt.addressIdConverter();

        if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1.selector())) {
            return decoder.decodeCreateFungibleTokenV1(inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2.selector())) {
            return decoder.decodeCreateFungibleTokenV2(inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3.selector())) {
            return decoder.decodeCreateFungibleTokenV3(inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1.selector())) {
            return decoder.decodeCreateFungibleTokenWithCustomFeesV1(
                    inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2.selector())) {
            return decoder.decodeCreateFungibleTokenWithCustomFeesV2(
                    inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3.selector())) {
            return decoder.decodeCreateFungibleTokenWithCustomFeesV3(
                    inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1.selector())) {
            return decoder.decodeCreateNonFungibleV1(inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2.selector())) {
            return decoder.decodeCreateNonFungibleV2(inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3.selector())) {
            return decoder.decodeCreateNonFungibleV3(inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(
                attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1.selector())) {
            return decoder.decodeCreateNonFungibleWithCustomFeesV1(
                    inputBytes, senderId, nativeOperations, addressIdConverter);
        } else if (Arrays.equals(
                attempt.selector(), CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2.selector())) {
            return decoder.decodeCreateNonFungibleWithCustomFeesV2(
                    inputBytes, senderId, nativeOperations, addressIdConverter);
        } else {
            return decoder.decodeCreateNonFungibleWithCustomFeesV3(
                    inputBytes, senderId, nativeOperations, addressIdConverter);
        }
    }
}
