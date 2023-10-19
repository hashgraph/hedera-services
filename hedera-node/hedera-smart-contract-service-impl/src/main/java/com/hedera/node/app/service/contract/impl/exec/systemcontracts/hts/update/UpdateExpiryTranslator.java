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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.EXPIRY_V2;

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

public class UpdateExpiryTranslator extends AbstractHtsCallTranslator {
    public static final Function UPDATE_TOKEN_EXPIRY_INFO_V1 =
            new Function("updateTokenExpiryInfo(address," + EXPIRY + ")", ReturnTypes.INT);
    public static final Function UPDATE_TOKEN_EXPIRY_INFO_V2 =
            new Function("updateTokenExpiryInfo(address," + EXPIRY_V2 + ")", ReturnTypes.INT);

    private final UpdateDecoder decoder;

    @Inject
    public UpdateExpiryTranslator(UpdateDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), UPDATE_TOKEN_EXPIRY_INFO_V1.selector())
                || Arrays.equals(attempt.selector(), UPDATE_TOKEN_EXPIRY_INFO_V2.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall<>(
                attempt, nominalBodyFor(attempt), SingleTransactionRecordBuilder.class);
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), UPDATE_TOKEN_EXPIRY_INFO_V1.selector())) {
            return decoder.decodeTokenUpdateExpiryV1(attempt);
        } else {
            return decoder.decodeTokenUpdateExpiryV2(attempt);
        }
    }
}
