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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.DispatchForResponseCodeHssCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code signSchedule()} calls to the HSS system contract.
 */
@Singleton
public class SignScheduleTranslator extends AbstractCallTranslator<HssCallAttempt> {
    public static final Function SIGN_SCHEDULE = new Function("signSchedule(address,bytes)", ReturnTypes.INT_64);
    public static final Function SIGN_SCHEDULE_PROXY = new Function("signSchedule()", ReturnTypes.INT_64);
    public static final Function AUTHORIZE_SCHEDULE = new Function("authorizeSchedule(address)", ReturnTypes.INT_64);

    @Inject
    public SignScheduleTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HssCallAttempt attempt) {
        requireNonNull(attempt);
        final var signScheduleEnabled =
                attempt.configuration().getConfigData(ContractsConfig.class).systemContractSignScheduleEnabled();
        final var authorizeScheduleEnabled =
                attempt.configuration().getConfigData(ContractsConfig.class).systemContractAuthorizeScheduleEnabled();
        return attempt.isSelectorIfConfigEnabled(signScheduleEnabled, SIGN_SCHEDULE_PROXY)
                || attempt.isSelectorIfConfigEnabled(authorizeScheduleEnabled, AUTHORIZE_SCHEDULE);
    }

    @Override
    public Call callFrom(@NonNull HssCallAttempt attempt) {
        final var body = bodyFor(scheduleIdFor(attempt));
        return new DispatchForResponseCodeHssCall(
                attempt, body, SignScheduleTranslator::gasRequirement, keySetFor(attempt));
    }

    /**
     * Calculates the gas requirement for a {@code signSchedule()} call.
     *
     * @param body the transaction body
     * @param systemContractGasCalculator the gas calculator
     * @param enhancement the enhancement
     * @param payerId the payer ID
     * @return the gas requirement
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.SCHEDULE_SIGN, payerId);
    }

    /**
     * Creates a transaction body for a {@code signSchedule()} or call.
     *
     * @param scheduleID the schedule ID
     * @return the transaction body
     */
    private TransactionBody bodyFor(@NonNull ScheduleID scheduleID) {
        requireNonNull(scheduleID);
        return TransactionBody.newBuilder()
                .scheduleSign(ScheduleSignTransactionBody.newBuilder()
                        .scheduleID(scheduleID)
                        .build())
                .build();
    }

    /**
     * Extracts the schedule ID from a {@code authorizeSchedule()} call or return the redirect schedule ID
     * if the call via the proxy contract
     *
     * @param attempt the call attempt
     * @return the schedule ID
     */
    private ScheduleID scheduleIdFor(@NonNull HssCallAttempt attempt) {
        requireNonNull(attempt);
        if (attempt.isSelector(SIGN_SCHEDULE_PROXY)) {
            final var scheduleID = attempt.redirectScheduleId();
            validateTrue(scheduleID != null, INVALID_SCHEDULE_ID);
            return attempt.redirectScheduleId();
        } else {
            final var call = AUTHORIZE_SCHEDULE.decodeCall(attempt.inputBytes());
            final Address scheduleAddress = call.get(0);
            final var number = numberOfLongZero(explicitFromHeadlong(scheduleAddress));
            final var schedule = attempt.enhancement().nativeOperations().getSchedule(number);
            validateTrue(schedule != null, INVALID_SCHEDULE_ID);
            return schedule.scheduleId();
        }
    }

    /**
     * Extracts the key set for a {@code signSchedule()} call.
     *
     * @param attempt the call attempt
     * @return the key set
     */
    private Set<Key> keySetFor(@NonNull HssCallAttempt attempt) {
        requireNonNull(attempt);
        if (attempt.isSelector(SIGN_SCHEDULE_PROXY)) {
            // If an Eth sender key is present, use it. Otherwise, use the account key if present.
            Key key = attempt.enhancement().systemOperations().maybeEthSenderKey();
            if (key != null) {
                return Set.of(key);
            }
            key = attempt.enhancement().nativeOperations().getAccountKey(attempt.originatorAccount());
            if (key != null) {
                return Set.of(key);
            }
            return emptySet();
        } else {
            final var contractNum = maybeMissingNumberOf(attempt.senderAddress(), attempt.nativeOperations());
            if (contractNum == MISSING_ENTITY_NUMBER) {
                return emptySet();
            }
            return Set.of(Key.newBuilder()
                    .contractID(ContractID.newBuilder().contractNum(contractNum).build())
                    .build());
        }
    }
}
