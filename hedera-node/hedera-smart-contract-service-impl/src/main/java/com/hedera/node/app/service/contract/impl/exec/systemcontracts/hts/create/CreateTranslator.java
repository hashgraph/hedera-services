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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN;

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

    private final CreateDecoder decoder;

    @Inject
    public CreateTranslator(CreateDecoder decoder) {
        // Dagger2
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN.selector());
    }

    @Override
    public FungibleCreateCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new FungibleCreateCall(
                attempt.enhancement(),
                nominalBodyFor(attempt),
                attempt.defaultVerificationStrategy(),
                attempt.senderAddress(),
                attempt.addressIdConverter());
    }

    private TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN.selector())) {
            return decoder.decodeCreateFungibleToken(attempt.inputBytes(), attempt.addressIdConverter());
        }
        throw new AssertionError("Add more cases");
    }
}
