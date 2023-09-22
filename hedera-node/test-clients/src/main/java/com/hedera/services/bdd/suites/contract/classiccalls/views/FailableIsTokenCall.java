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

package com.hedera.services.bdd.suites.contract.classiccalls.views;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.NO_REASON_TO_FAIL;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.AbstractFailableCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.List;

public class FailableIsTokenCall extends AbstractFailableCall {
    private static final Function SIGNATURE = new Function("isToken(address)", "(int64,bool)");

    public FailableIsTokenCall() {
        super(EnumSet.of(INVALID_TOKEN_ID_FAILURE, NO_REASON_TO_FAIL));
    }

    @Override
    public void assertExpectedRecords(List<TransactionRecord> records, HapiSpec spec, ClassicFailureMode mode) {
        rememberRecordsForMode(mode, records);
    }

    @Override
    public void assertExpectedResult(ContractFunctionResult result, HapiSpec spec, ClassicFailureMode mode) {
        rememberResultForStaticMode(mode, result);
    }

    @Override
    public String name() {
        return "IsToken";
    }

    @Override
    public byte[] encodedFailureFromInventory(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        if (mode == NO_REASON_TO_FAIL) {
            final var tokenAddress =
                    idAsHeadlongAddress(spec.registry().getTokenID(ClassicInventory.VALID_FUNGIBLE_TOKEN_IDS[0]));
            return SIGNATURE.encodeCallWithArgs(tokenAddress).array();
        } else {
            return SIGNATURE.encodeCallWithArgs(INVALID_TOKEN_ADDRESS).array();
        }
    }

    @Override
    public boolean staticCallOk() {
        return true;
    }
}
