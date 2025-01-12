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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.getscheduledinfo.GetScheduledInfoTranslator.GET_SCHEDULED_FUNGIBLE_TOKEN_CREATE_TRANSACTION;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.TokenTupleUtils.fungibleTokenInfoTupleFor;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class GetScheduledFungibleTokenCreateCall extends AbstractCall {

    private final ScheduleID scheduleID;
    private final Configuration configuration;

    public GetScheduledFungibleTokenCreateCall(
            @NonNull SystemContractGasCalculator gasCalculator,
            @NonNull Enhancement enhancement,
            @NonNull Configuration configuration,
            @NonNull final ScheduleID scheduleID) {
        super(gasCalculator, enhancement, true);
        this.configuration = configuration;
        this.scheduleID = scheduleID;
    }

    @NonNull
    @Override
    public PricedResult execute(MessageFrame frame) {
        final var schedule = nativeOperations().getSchedule(scheduleID.scheduleNum());
        // Validate that given schedule exists
        if (schedule == null) {
            return gasOnly(revertResult(RECORD_NOT_FOUND, gasCalculator.viewGasRequirement()), RECORD_NOT_FOUND, true);
        }
        // Validate that give schedule is a token creation schedule
        if (schedule.scheduledTransaction() == null
                || schedule.scheduledTransaction().tokenCreation() == null) {
            return gasOnly(
                    revertResult(INVALID_SCHEDULE_ID, gasCalculator.viewGasRequirement()), INVALID_SCHEDULE_ID, true);
        }
        // Validate that given schedule is a fungible token creation schedule
        final var tokenCreation = schedule.scheduledTransaction().tokenCreation();
        if (tokenCreation.tokenType() != TokenType.FUNGIBLE_COMMON) {
            return gasOnly(
                    revertResult(INVALID_SCHEDULE_ID, gasCalculator.viewGasRequirement()), INVALID_SCHEDULE_ID, true);
        }

        // Return the token create transaction body parsed to fungible token info tuple
        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var ledgerId = Bytes.wrap(ledgerConfig.id().toByteArray()).toString();
        return gasOnly(
                successResult(
                        GET_SCHEDULED_FUNGIBLE_TOKEN_CREATE_TRANSACTION
                                .getOutputs()
                                .encodeElements(
                                        SUCCESS.protoOrdinal(), fungibleTokenInfoTupleFor(tokenCreation, ledgerId, 1)),
                        gasCalculator.viewGasRequirement()),
                SUCCESS,
                true);
    }
}
