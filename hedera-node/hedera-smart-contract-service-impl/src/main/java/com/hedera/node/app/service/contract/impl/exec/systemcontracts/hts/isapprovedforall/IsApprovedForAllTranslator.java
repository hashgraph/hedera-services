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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code isApprovedForAll} calls to the HTS system contract.
 */
@Singleton
public class IsApprovedForAllTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    public static final Function CLASSIC_IS_APPROVED_FOR_ALL =
            new Function("isApprovedForAll(address,address,address)", "(int64,bool)");
    public static final Function ERC_IS_APPROVED_FOR_ALL = new Function("isApprovedForAll(address,address)", "(bool)");

    @Inject
    public IsApprovedForAllTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(CLASSIC_IS_APPROVED_FOR_ALL, ERC_IS_APPROVED_FOR_ALL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IsApprovedForAllCall callFrom(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(ERC_IS_APPROVED_FOR_ALL)) {
            final var args = ERC_IS_APPROVED_FOR_ALL.decodeCall(attempt.input().toArrayUnsafe());
            return new IsApprovedForAllCall(
                    attempt.systemContractGasCalculator(),
                    attempt.enhancement(),
                    attempt.redirectToken(),
                    args.get(0),
                    args.get(1),
                    true);
        } else {
            final var args =
                    CLASSIC_IS_APPROVED_FOR_ALL.decodeCall(attempt.input().toArrayUnsafe());
            final var token = attempt.linkedToken(fromHeadlongAddress(args.get(0)));
            return new IsApprovedForAllCall(
                    attempt.systemContractGasCalculator(),
                    attempt.enhancement(),
                    token,
                    args.get(1),
                    args.get(2),
                    false);
        }
    }
}
