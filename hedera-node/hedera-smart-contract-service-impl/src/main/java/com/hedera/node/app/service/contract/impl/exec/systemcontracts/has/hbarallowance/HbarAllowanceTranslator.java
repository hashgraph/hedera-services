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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code hbarApprove()} calls to the HAS system contract.
 */
@Singleton
public class HbarAllowanceTranslator extends AbstractCallTranslator<HasCallAttempt> {

    public static final Function HBAR_ALLOWANCE_PROXY =
            new Function("hbarAllowance(address)", ReturnTypes.RESPONSE_CODE_INT256);

    public static final Function HBAR_ALLOWANCE =
            new Function("hbarAllowance(address,address)", ReturnTypes.RESPONSE_CODE_INT256);

    @Inject
    public HbarAllowanceTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        return attempt.isSelector(HBAR_ALLOWANCE, HBAR_ALLOWANCE_PROXY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Call callFrom(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        if (attempt.isSelector(HBAR_ALLOWANCE)) {
            return decodeAllowance(attempt);
        } else if (attempt.isSelector(HBAR_ALLOWANCE_PROXY)) {
            return decodeAllowanceProxy(attempt);
        }
        return null;
    }

    @NonNull
    private Call decodeAllowance(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        final var call = HBAR_ALLOWANCE.decodeCall(attempt.inputBytes());

        var owner = attempt.addressIdConverter().convert(call.get(0));
        var spender = attempt.addressIdConverter().convert(call.get(1));
        return new HbarAllowanceCall(attempt, owner, spender);
    }

    @NonNull
    private Call decodeAllowanceProxy(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        final var call = HBAR_ALLOWANCE_PROXY.decodeCall(attempt.inputBytes());
        var spender = attempt.addressIdConverter().convert(call.get(0));
        return new HbarAllowanceCall(attempt, attempt.redirectAccountId(), spender);
    }
}
