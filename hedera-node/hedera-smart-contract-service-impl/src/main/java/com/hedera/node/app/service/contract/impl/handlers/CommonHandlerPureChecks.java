/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.handlers;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class CommonHandlerPureChecks {

    @NonNull
    public static ContractID simpleValidateContractId(@Nullable final ContractID subject) throws PreCheckException {
        final var result = simpleValidateNullableContractId(subject);
        if (result == null) throwInvalidContractId();
        return result;
    }

    @Nullable
    public static ContractID simpleValidateNullableContractId(@Nullable final ContractID subject)
            throws PreCheckException {
        if (subject == null) return null;

        switch (subject.contract().kind()) {
            case UNSET -> throwInvalidContractId();
            case CONTRACT_NUM -> {
                final long num = subject.contractNumOrElse(0L);
                if (num <= 0) throwInvalidContractId();
            }
            case EVM_ADDRESS -> {
                final var address = subject.evmAddressOrElse(Bytes.EMPTY);
                if (address.length() < 1) throwInvalidContractId();
            }
        }

        // FUTURE: Shard/realm NOT checked since they're dynamic properties (though they should never change in a
        // running node).  See comment over at com.hedera.node.app.spi.validation.Validations.java

        return subject;
    }

    private static void throwInvalidContractId() throws PreCheckException {
        throw new PreCheckException(ResponseCodeEnum.INVALID_CONTRACT_ID);
    }
}
