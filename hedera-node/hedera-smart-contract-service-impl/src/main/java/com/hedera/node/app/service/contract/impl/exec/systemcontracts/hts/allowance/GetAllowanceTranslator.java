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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code allowance()} calls to the HTS system contract.
 */
@Singleton
public class GetAllowanceTranslator extends AbstractHtsCallTranslator {

    public static final Function GET_ALLOWANCE =
            new Function("allowance(address,address,address)", ReturnTypes.RESPONSE_CODE_UINT256);
    public static final Function ERC_GET_ALLOWANCE = new Function("allowance(address,address)", ReturnTypes.UINT256);

    @Inject
    public GetAllowanceTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return matchesClassicSelector(attempt.selector()) || matchesErcSelector(attempt.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        if (matchesErcSelector(attempt.selector())) {
            final var call = GetAllowanceTranslator.ERC_GET_ALLOWANCE.decodeCall(attempt.inputBytes());
            return new GetAllowanceCall(
                    attempt.addressIdConverter(),
                    attempt.enhancement(),
                    attempt.redirectToken(),
                    call.get(0),
                    call.get(1),
                    true,
                    attempt.isStaticCall());
        } else {
            final var call = GetAllowanceTranslator.GET_ALLOWANCE.decodeCall(attempt.inputBytes());
            final Address token = call.get(0);
            final Address owner = call.get(1);
            final Address spender = call.get(2);
            return new GetAllowanceCall(
                    attempt.addressIdConverter(),
                    attempt.enhancement(),
                    attempt.linkedToken(ConversionUtils.fromHeadlongAddress(token)),
                    owner,
                    spender,
                    false,
                    attempt.isStaticCall());
        }
    }

    private boolean matchesErcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_GET_ALLOWANCE.selector());
    }

    private boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, GET_ALLOWANCE.selector());
    }
}
