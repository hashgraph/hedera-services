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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asExactLongValueOrZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetApprovedTranslator extends AbstractHtsCallTranslator {

    public static final Function HAPI_GET_APPROVED = new Function("getApproved(address,uint256)", "(int32,address)");
    public static final Function ERC_GET_APPROVED = new Function("getApproved(uint256)", ReturnTypes.ADDRESS);

    @Inject
    public GetApprovedTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return (attempt.isTokenRedirect() && matchesErcSelector(attempt.selector()))
                || (!attempt.isTokenRedirect() && matchesClassicSelector(attempt.selector()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GetApprovedCall callFrom(@NonNull final HtsCallAttempt attempt) {
        if (matchesErcSelector(attempt.selector())) {
            final var args = ERC_GET_APPROVED.decodeCall(attempt.input().toArrayUnsafe());
            return new GetApprovedCall(
                    attempt.enhancement(),
                    attempt.redirectToken(),
                    asExactLongValueOrZero(args.get(0)),
                    true,
                    attempt.isStaticCall());
        } else {
            final var args = HAPI_GET_APPROVED.decodeCall(attempt.input().toArrayUnsafe());
            final var token = attempt.linkedToken(fromHeadlongAddress(args.get(0)));
            return new GetApprovedCall(
                    attempt.enhancement(), token, asExactLongValueOrZero(args.get(1)), false, attempt.isStaticCall());
        }
    }

    private static boolean matchesErcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_GET_APPROVED.selector());
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, HAPI_GET_APPROVED.selector());
    }
}
