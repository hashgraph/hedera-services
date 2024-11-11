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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isassociated;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IsAssociatedTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /** Selector for isAssociated() method. */
    public static final Function IS_ASSOCIATED = new Function("isAssociated()", ReturnTypes.BOOL);

    /**
     * Default constructor for injection.
     */
    @Inject
    public IsAssociatedTranslator() {
        // Dagger2
    }

    @Override
    public final boolean matches(@NonNull final HtsCallAttempt attempt) {
        return attempt.isTokenRedirect() && attempt.isSelector(IS_ASSOCIATED);
    }

    @Override
    public final Call callFrom(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        return new IsAssociatedCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.senderId(),
                attempt.redirectToken());
    }
}
