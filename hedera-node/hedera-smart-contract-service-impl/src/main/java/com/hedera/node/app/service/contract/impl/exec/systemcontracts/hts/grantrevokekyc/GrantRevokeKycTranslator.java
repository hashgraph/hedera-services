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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall.FailureCustomizer.NOOP_CUSTOMIZER;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.*;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates grantKyc and revokeKyc calls to the HTS system contract. There are no special cases for
 * these calls, so the returned {@link Call} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
@Singleton
public class GrantRevokeKycTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /**
     * Selector for grantTokenKyc(address,address) method.
     */
    public static final Function GRANT_KYC = new Function("grantTokenKyc(address,address)", ReturnTypes.INT_64);
    /**
     * Selector for revokeTokenKyc(address,address) method.
     */
    public static final Function REVOKE_KYC = new Function("revokeTokenKyc(address,address)", ReturnTypes.INT_64);

    private final GrantRevokeKycDecoder decoder;

    /**
     * @param decoder the decoder to be used for grand / revoke kyc
     */
    @Inject
    public GrantRevokeKycTranslator(@NonNull GrantRevokeKycDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return attempt.isSelector(GRANT_KYC, REVOKE_KYC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall(
                attempt,
                bodyForClassic(attempt),
                attempt.isSelector(GRANT_KYC)
                        ? GrantRevokeKycTranslator::grantGasRequirement
                        : GrantRevokeKycTranslator::revokeGasRequirement,
                NOOP_CUSTOMIZER);
    }

    /**
     * @param body the transaction body to be dispatched
     * @param systemContractGasCalculator the gas calculator for the system contract
     * @param enhancement the enhancement to use
     * @param payerId the payer of the transaction
     * @return the gas requirement for grant kyc calls to HTS system contract
     */
    public static long grantGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.GRANT_KYC, payerId);
    }

    /**
     * @param body the transaction body to be dispatched
     * @param systemContractGasCalculator the gas calculator for the system contract
     * @param enhancement the enhancement to use
     * @param payerId the payer of the transaction
     * @return the gas requirement for revoke kyc calls to HTS system contract
     */
    public static long revokeGasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.REVOKE_KYC, payerId);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(GRANT_KYC)) {
            return decoder.decodeGrantKyc(attempt);
        } else {
            return decoder.decodeRevokeKyc(attempt);
        }
    }
}
