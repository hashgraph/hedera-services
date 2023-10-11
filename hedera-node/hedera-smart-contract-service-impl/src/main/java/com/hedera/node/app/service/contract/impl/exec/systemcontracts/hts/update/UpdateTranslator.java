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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.TOKEN_KEY;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

public class UpdateTranslator extends AbstractHtsCallTranslator {
    private static final String UPDATE_TOKEN_INFO_STRING = "updateTokenInfo(address,";
    private static final String HEDERA_TOKEN_STRUCT =
            "(string,string,address,string,bool,uint32,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";
    private static final String HEDERA_TOKEN_STRUCT_V2 =
            "(string,string,address,string,bool,int64,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";
    private static final String HEDERA_TOKEN_STRUCT_V3 =
            "(string,string,address,string,bool,int64,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY_V2 + ")";
    public static final Function TOKEN_UPDATE_INFO_FUNCTION =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT + ")", ReturnTypes.INT);
    public static final Function TOKEN_UPDATE_INFO_FUNCTION_V2 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V2 + ")", ReturnTypes.INT);
    public static final Function TOKEN_UPDATE_INFO_FUNCTION_V3 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V3 + ")", ReturnTypes.INT);

    private final UpdateDecoder decoder;

    @Inject
    public UpdateTranslator(UpdateDecoder decoder) {
        // Dagger2
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), TOKEN_UPDATE_INFO_FUNCTION.selector())
                || Arrays.equals(attempt.selector(), TOKEN_UPDATE_INFO_FUNCTION_V2.selector())
                || Arrays.equals(attempt.selector(), TOKEN_UPDATE_INFO_FUNCTION_V3.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall<>(
                attempt, nominalBodyFor(attempt), SingleTransactionRecordBuilder.class);
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), TOKEN_UPDATE_INFO_FUNCTION.selector())) {
            return decoder.decodeTokenUpdateV1(attempt);
        } else if (Arrays.equals(attempt.selector(), TOKEN_UPDATE_INFO_FUNCTION_V2.selector())) {
            return decoder.decodeTokenUpdateV2(attempt);
        } else {
            return decoder.decodeTokenUpdateV3(attempt);
        }
    }
}
