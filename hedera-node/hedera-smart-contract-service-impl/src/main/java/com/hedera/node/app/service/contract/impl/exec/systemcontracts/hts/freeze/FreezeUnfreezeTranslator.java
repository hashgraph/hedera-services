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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code freeze()}, {@code unfreeze()} calls to the HTS system contract.
 */
@Singleton
public class FreezeUnfreezeTranslator extends AbstractHtsCallTranslator {
    public static final Function FREEZE = new Function("freezeToken(address,address)", ReturnTypes.INT_64);
    public static final Function UNFREEZE = new Function("unfreezeToken(address,address)", ReturnTypes.INT_64);

    @Inject
    public FreezeUnfreezeTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), FREEZE.selector())
                || Arrays.equals(attempt.selector(), UNFREEZE.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        final Tuple call;
        if (Arrays.equals(selector, FREEZE.selector())) {
            call = FreezeUnfreezeTranslator.FREEZE.decodeCall(attempt.input().toArrayUnsafe());
            return new FreezeCall(
                    attempt.enhancement(),
                    attempt.addressIdConverter(),
                    attempt.defaultVerificationStrategy(),
                    attempt.senderId(),
                    call.get(0),
                    call.get(1));
        } else {
            call = FreezeUnfreezeTranslator.UNFREEZE.decodeCall(attempt.input().toArrayUnsafe());
            return new UnfreezeCall(
                    attempt.enhancement(),
                    attempt.addressIdConverter(),
                    attempt.defaultVerificationStrategy(),
                    attempt.senderId(),
                    call.get(0),
                    call.get(1));
        }
    }
}
