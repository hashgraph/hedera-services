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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Translates setApprovalForAll (including ERC) call to the HTS system contract. There are no special cases for these
 * calls, so the returned {@link Call} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
public class SetApprovalForAllTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    /** Selector for setApprovalForAll(address,address,bool) method. */
    public static final Function SET_APPROVAL_FOR_ALL =
            new Function("setApprovalForAll(address,address,bool)", ReturnTypes.INT);
    /** Selector for setApprovalForAll(address,bool) method. */
    public static final Function ERC721_SET_APPROVAL_FOR_ALL =
            new Function("setApprovalForAll(address,bool)", ReturnTypes.INT);

    private final SetApprovalForAllDecoder decoder;

    /**
     * @param decoder the decoder to use for approve calls
     */
    @Inject
    public SetApprovalForAllTranslator(final SetApprovalForAllDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isTokenRedirect()
                ? attempt.isSelector(ERC721_SET_APPROVAL_FOR_ALL)
                : attempt.isSelector(SET_APPROVAL_FOR_ALL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var result = bodyForClassic(attempt);
        return new SetApprovalForAllCall(
                attempt,
                result,
                SetApprovalForAllTranslator::gasRequirement,
                attempt.isSelector(ERC721_SET_APPROVAL_FOR_ALL));
    }

    /**
     * @param body                          the transaction body to be dispatched
     * @param systemContractGasCalculator   the gas calculator for the system contract
     * @param enhancement                   the enhancement to use
     * @param payerId                       the payer of the transaction
     * @return the required gas
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.APPROVE, payerId);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(SET_APPROVAL_FOR_ALL)) {
            return decoder.decodeSetApprovalForAll(attempt);
        }
        return decoder.decodeSetApprovalForAllERC(attempt);
    }
}
