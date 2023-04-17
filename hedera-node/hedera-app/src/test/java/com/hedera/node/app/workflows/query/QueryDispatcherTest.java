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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoQuery;
import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.contract.ContractGetBytecodeQuery;
import com.hedera.hapi.node.contract.ContractGetInfoQuery;
import com.hedera.hapi.node.contract.ContractGetRecordsQuery;
import com.hedera.hapi.node.contract.GetBySolidityIDQuery;
import com.hedera.hapi.node.file.FileGetContentsQuery;
import com.hedera.hapi.node.file.FileGetInfoQuery;
import com.hedera.hapi.node.network.NetworkGetExecutionTimeQuery;
import com.hedera.hapi.node.network.NetworkGetVersionInfoQuery;
import com.hedera.hapi.node.scheduled.ScheduleGetInfoQuery;
import com.hedera.hapi.node.token.CryptoGetAccountBalanceQuery;
import com.hedera.hapi.node.token.CryptoGetAccountRecordsQuery;
import com.hedera.hapi.node.token.CryptoGetInfoQuery;
import com.hedera.hapi.node.token.CryptoGetLiveHashQuery;
import com.hedera.hapi.node.token.CryptoGetStakersQuery;
import com.hedera.hapi.node.token.GetAccountDetailsQuery;
import com.hedera.hapi.node.token.TokenGetAccountNftInfosQuery;
import com.hedera.hapi.node.token.TokenGetInfoQuery;
import com.hedera.hapi.node.token.TokenGetNftInfoQuery;
import com.hedera.hapi.node.token.TokenGetNftInfosQuery;
import com.hedera.hapi.node.transaction.GetByKeyQuery;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionGetReceiptQuery;
import com.hedera.hapi.node.transaction.TransactionGetRecordQuery;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallLocalHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBySolidityIDHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBytecodeHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetInfoHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetRecordsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
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
import com.hedera.node.app.spi.workflows.QueryHandler;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryDispatcherTest {
    @Mock
    private ConsensusGetTopicInfoHandler consensusGetTopicInfoHandler;

    @Mock
    private ContractGetBySolidityIDHandler contractGetBySolidityIDHandler;

    @Mock
    private ContractCallLocalHandler contractCallLocalHandler;

    @Mock
    private ContractGetInfoHandler contractGetInfoHandler;

    @Mock
    private ContractGetBytecodeHandler contractGetBytecodeHandler;

    @Mock
    private ContractGetRecordsHandler contractGetRecordsHandler;

    @Mock
    private CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler;

    @Mock
    private CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler;

    @Mock
    private CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler;

    @Mock
    private CryptoGetLiveHashHandler cryptoGetLiveHashHandler;

    @Mock
    private CryptoGetStakersHandler cryptoGetStakersHandler;

    @Mock
    private FileGetContentsHandler fileGetContentsHandler;

    @Mock
    private FileGetInfoHandler fileGetInfoHandler;

    @Mock
    private NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;

    @Mock
    private NetworkGetByKeyHandler networkGetByKeyHandler;

    @Mock
    private NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler;

    @Mock
    private NetworkGetVersionInfoHandler networkGetVersionInfoHandler;

    @Mock
    private NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;

    @Mock
    private NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;

    @Mock
    private ScheduleGetInfoHandler scheduleGetInfoHandler;

    @Mock
    private TokenGetInfoHandler tokenGetInfoHandler;

    @Mock
    private TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler;

    @Mock
    private TokenGetNftInfoHandler tokenGetNftInfoHandler;

    @Mock
    private TokenGetNftInfosHandler tokenGetNftInfosHandler;

    private QueryHandlers handlers;

    private QueryDispatcher dispatcher;

    @BeforeEach
    void setup() {
        handlers = new QueryHandlers(
                consensusGetTopicInfoHandler,
                contractGetBySolidityIDHandler,
                contractCallLocalHandler,
                contractGetInfoHandler,
                contractGetBytecodeHandler,
                contractGetRecordsHandler,
                cryptoGetAccountBalanceHandler,
                cryptoGetAccountInfoHandler,
                cryptoGetAccountRecordsHandler,
                cryptoGetLiveHashHandler,
                cryptoGetStakersHandler,
                fileGetContentsHandler,
                fileGetInfoHandler,
                networkGetAccountDetailsHandler,
                networkGetByKeyHandler,
                networkGetExecutionTimeHandler,
                networkGetVersionInfoHandler,
                networkTransactionGetReceiptHandler,
                networkTransactionGetRecordHandler,
                scheduleGetInfoHandler,
                tokenGetInfoHandler,
                tokenGetAccountNftInfosHandler,
                tokenGetNftInfoHandler,
                tokenGetNftInfosHandler);

        dispatcher = new QueryDispatcher(handlers);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new QueryDispatcher(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testGetHandlerWithIllegalParameters() {
        assertThatThrownBy(() -> dispatcher.getHandler(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetHandlersWithNoQuerySet() {
        // given
        final var query = Query.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.getHandler(query)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testGetHandler(final Query query, final Function<QueryHandlers, QueryHandler> getter) {
        // when
        final var result = dispatcher.getHandler(query);

        // then
        Assertions.assertThat(result).isEqualTo(getter.apply(handlers));
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                Arguments.of(
                        Query.newBuilder()
                                .consensusGetTopicInfo(
                                        ConsensusGetTopicInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::consensusGetTopicInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .getBySolidityID(
                                        GetBySolidityIDQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetBySolidityIDHandler),
                Arguments.of(
                        Query.newBuilder()
                                .contractCallLocal(
                                        ContractCallLocalQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractCallLocalHandler),
                Arguments.of(
                        Query.newBuilder()
                                .contractGetInfo(
                                        ContractGetInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .contractGetBytecode(
                                        ContractGetBytecodeQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetBytecodeHandler),
                Arguments.of(
                        Query.newBuilder()
                                .contractGetRecords(
                                        ContractGetRecordsQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetRecordsHandler),
                Arguments.of(
                        Query.newBuilder()
                                .cryptogetAccountBalance(CryptoGetAccountBalanceQuery.newBuilder()
                                        .build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetAccountBalanceHandler),
                Arguments.of(
                        Query.newBuilder()
                                .cryptoGetInfo(CryptoGetInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetAccountInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .cryptoGetAccountRecords(CryptoGetAccountRecordsQuery.newBuilder()
                                        .build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetAccountRecordsHandler),
                Arguments.of(
                        Query.newBuilder()
                                .cryptoGetLiveHash(
                                        CryptoGetLiveHashQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetLiveHashHandler),
                Arguments.of(
                        Query.newBuilder()
                                .cryptoGetProxyStakers(
                                        CryptoGetStakersQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetStakersHandler),
                Arguments.of(
                        Query.newBuilder()
                                .fileGetContents(
                                        FileGetContentsQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::fileGetContentsHandler),
                Arguments.of(
                        Query.newBuilder()
                                .fileGetInfo(FileGetInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::fileGetInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .scheduleGetInfo(
                                        ScheduleGetInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::scheduleGetInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .tokenGetInfo(TokenGetInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .tokenGetAccountNftInfos(TokenGetAccountNftInfosQuery.newBuilder()
                                        .build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetAccountNftInfosHandler),
                Arguments.of(
                        Query.newBuilder()
                                .tokenGetNftInfo(
                                        TokenGetNftInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetNftInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .tokenGetNftInfos(
                                        TokenGetNftInfosQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetNftInfosHandler),
                Arguments.of(
                        Query.newBuilder()
                                .accountDetails(
                                        GetAccountDetailsQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetAccountDetailsHandler),
                Arguments.of(
                        Query.newBuilder()
                                .networkGetVersionInfo(
                                        NetworkGetVersionInfoQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetVersionInfoHandler),
                Arguments.of(
                        Query.newBuilder()
                                .getByKey(GetByKeyQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetByKeyHandler),
                Arguments.of(
                        Query.newBuilder()
                                .networkGetExecutionTime(NetworkGetExecutionTimeQuery.newBuilder()
                                        .build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetExecutionTimeHandler),
                Arguments.of(
                        Query.newBuilder()
                                .transactionGetReceipt(
                                        TransactionGetReceiptQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkTransactionGetReceiptHandler),
                Arguments.of(
                        Query.newBuilder()
                                .transactionGetRecord(
                                        TransactionGetRecordQuery.newBuilder().build())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkTransactionGetRecordHandler));
    }
}
