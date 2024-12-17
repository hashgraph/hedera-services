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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultfreezestatus;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Translates {@code getTokenDefaultFreezeStatus()} calls to the HTS system contract.
 */
public class DefaultFreezeStatusTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /** Selector for getTokenDefaultFreezeStatus(address) method. */
    public static final Function DEFAULT_FREEZE_STATUS =
            new Function("getTokenDefaultFreezeStatus(address)", ReturnTypes.RESPONSE_CODE_BOOL);

    /**
     * Default constructor for injection.
     */
    @Inject
    public DefaultFreezeStatusTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(DEFAULT_FREEZE_STATUS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var args = DEFAULT_FREEZE_STATUS.decodeCall(attempt.input().toArrayUnsafe());
        final var token = attempt.linkedToken(fromHeadlongAddress(args.get(0)));
        return new DefaultFreezeStatusCall(
                attempt.systemContractGasCalculator(), attempt.enhancement(), attempt.isStaticCall(), token);
    }
}
