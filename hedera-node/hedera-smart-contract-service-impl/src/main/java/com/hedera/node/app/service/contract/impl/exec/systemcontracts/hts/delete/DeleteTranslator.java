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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.delete;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;

public class DeleteTranslator extends AbstractHtsCallTranslator {
    public static final Function DELETE_TOKEN = new Function("deleteToken(address)", ReturnTypes.INT);

    @Inject
    public DeleteTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), DELETE_TOKEN.selector());
    }

    @Override
    public DeleteCall callFrom(@NonNull HtsCallAttempt attempt) {
        final Tuple call = DELETE_TOKEN.decodeCall(attempt.input().toArrayUnsafe());
        final var token = attempt.linkedToken(Address.fromHexString(call.get(0).toString()));
        if (token == null) {
            return null;
        } else {
            return new DeleteCall(
                    attempt.enhancement(),
                    ConversionUtils.asTokenId(call.get(0)),
                    attempt.defaultVerificationStrategy(),
                    attempt.senderAddress(),
                    attempt.addressIdConverter());
        }
    }
}
