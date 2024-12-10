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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.iskyc;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class IsKycTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /** Selector for isKyc(address,address) method. */
    public static final Function IS_KYC = new Function("isKyc(address,address)", ReturnTypes.RESPONSE_CODE_BOOL);

    /**
     * Default constructor for injection.
     */
    @Inject
    public IsKycTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(IS_KYC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var args = IS_KYC.decodeCall(attempt.input().toArrayUnsafe());
        final var token = attempt.linkedToken(fromHeadlongAddress(args.get(0)));
        return new IsKycCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.isStaticCall(),
                token,
                args.get(1));
    }
}
