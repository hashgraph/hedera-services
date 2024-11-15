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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.signschedule;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;

/**
 * Translates {@code signSchedule()} calls to the HSS system contract.
 */
@Singleton
public class SignScheduleTranslator extends AbstractCallTranslator<HssCallAttempt> {
    /** Selector for signSchedule(address,bytes) method. */
    public static final Function SIGN_SCHEDULE = new Function("signSchedule(address,bytes)", ReturnTypes.INT_64);

    // Future: implement fully in a future pr.  This stub class only used for testing purposes
    @Override
    public boolean matches(@NotNull HssCallAttempt attempt) {
        requireNonNull(attempt);
        return attempt.isSelector(SIGN_SCHEDULE);
    }

    @Override
    public Call callFrom(@NotNull HssCallAttempt attempt) {
        return new SignScheduleCall(attempt);
    }
}
