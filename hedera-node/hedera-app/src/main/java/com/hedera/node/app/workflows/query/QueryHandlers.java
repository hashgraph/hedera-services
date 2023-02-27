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

package com.hedera.node.app.workflows.query;

import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandlerImpl;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallLocalHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBySolidityIDHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBytecodeHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetInfoHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetRecordsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandlerImpl;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandlerImpl;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetVersionInfoHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleGetInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountBalanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountRecordsHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetStakersHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetAccountNftInfosHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetNftInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetNftInfosHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A record that contains all {@link com.hedera.node.app.spi.workflows.QueryHandler}s that are available in the app
 */
public record QueryHandlers(
        @NonNull ConsensusGetTopicInfoHandlerImpl consensusGetTopicInfoHandler,
        @NonNull ContractGetBySolidityIDHandler contractGetBySolidityIDHandler,
        @NonNull ContractCallLocalHandler contractCallLocalHandler,
        @NonNull ContractGetInfoHandler contractGetInfoHandler,
        @NonNull ContractGetBytecodeHandler contractGetBytecodeHandler,
        @NonNull ContractGetRecordsHandler contractGetRecordsHandler,
        @NonNull CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler,
        @NonNull CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler,
        @NonNull CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler,
        @NonNull CryptoGetLiveHashHandler cryptoGetLiveHashHandler,
        @NonNull CryptoGetStakersHandler cryptoGetStakersHandler,
        @NonNull FileGetContentsHandlerImpl fileGetContentsHandler,
        @NonNull FileGetInfoHandlerImpl fileGetInfoHandler,
        @NonNull NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler,
        @NonNull NetworkGetByKeyHandler networkGetByKeyHandler,
        @NonNull NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler,
        @NonNull NetworkGetVersionInfoHandler networkGetVersionInfoHandler,
        @NonNull NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler,
        @NonNull NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler,
        @NonNull ScheduleGetInfoHandler scheduleGetInfoHandler,
        @NonNull TokenGetInfoHandler tokenGetInfoHandler,
        @NonNull TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler,
        @NonNull TokenGetNftInfoHandler tokenGetNftInfoHandler,
        @NonNull TokenGetNftInfosHandler tokenGetNftInfosHandler) {
}
