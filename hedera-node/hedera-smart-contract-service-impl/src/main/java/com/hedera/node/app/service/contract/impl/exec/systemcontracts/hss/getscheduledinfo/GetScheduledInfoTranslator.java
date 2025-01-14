/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.getscheduledinfo;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.addressToScheduleID;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetScheduledInfoTranslator extends AbstractCallTranslator<HssCallAttempt> {
    public static final Function GET_SCHEDULED_FUNGIBLE_TOKEN_CREATE_TRANSACTION = new Function(
            "getScheduledFungibleTokenCreateTransaction(address)", ReturnTypes.RESPONSE_CODE_FUNGIBLE_TOKEN_INFO);
    public static final Function GET_SCHEDULED_NON_FUNGIBLE_TOKEN_CREATE_TRANSACTION = new Function(
            "getScheduledNonFungibleTokenCreateTransaction(address)",
            ReturnTypes.RESPONSE_CODE_NON_FUNGIBLE_TOKEN_INFO);

    // Tuple index for the schedule address
    private static final int SCHEDULE_ADDRESS = 0;

    @Inject
    public GetScheduledInfoTranslator() {
        // Dagger
    }

    @Override
    public boolean matches(@NonNull final HssCallAttempt attempt) {
        return attempt.isSelector(
                GET_SCHEDULED_FUNGIBLE_TOKEN_CREATE_TRANSACTION, GET_SCHEDULED_NON_FUNGIBLE_TOKEN_CREATE_TRANSACTION);
    }

    @Override
    public Call callFrom(@NonNull final HssCallAttempt attempt) {
        return attempt.isSelector(GET_SCHEDULED_FUNGIBLE_TOKEN_CREATE_TRANSACTION)
                ? getFungibleTokenCreateCall(attempt)
                : getNonFungibleTokenCreateCall(attempt);
    }

    private Call getFungibleTokenCreateCall(@NonNull final HssCallAttempt attempt) {
        final var call = GET_SCHEDULED_FUNGIBLE_TOKEN_CREATE_TRANSACTION.decodeCall(attempt.inputBytes());
        final Address scheduleAddress = call.get(SCHEDULE_ADDRESS);
        return new GetScheduledFungibleTokenCreateCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.configuration(),
                addressToScheduleID(scheduleAddress));
    }

    private Call getNonFungibleTokenCreateCall(@NonNull final HssCallAttempt attempt) {
        final var call = GET_SCHEDULED_NON_FUNGIBLE_TOKEN_CREATE_TRANSACTION.decodeCall(attempt.inputBytes());
        final Address scheduleAddress = call.get(SCHEDULE_ADDRESS);
        return new GetScheduledNonFungibleTokenCreateCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                attempt.configuration(),
                addressToScheduleID(scheduleAddress));
    }
}
