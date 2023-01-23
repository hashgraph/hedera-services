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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallLocalHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBySolidityIDHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBytecodeHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetInfoHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetRecordsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
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
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetVersionInfoHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.CryptoGetStakersQuery;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoQuery;
import com.hederahashgraph.api.proto.java.GetAccountDetailsQuery;
import com.hederahashgraph.api.proto.java.GetByKeyQuery;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import java.util.function.Function;
import java.util.stream.Stream;
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
    @Mock private ConsensusGetTopicInfoHandler consensusGetTopicInfoHandler;
    @Mock private ContractGetBySolidityIDHandler contractGetBySolidityIDHandler;
    @Mock private ContractCallLocalHandler contractCallLocalHandler;
    @Mock private ContractGetInfoHandler contractGetInfoHandler;
    @Mock private ContractGetBytecodeHandler contractGetBytecodeHandler;
    @Mock private ContractGetRecordsHandler contractGetRecordsHandler;
    @Mock private CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler;
    @Mock private CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler;
    @Mock private CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler;
    @Mock private CryptoGetLiveHashHandler cryptoGetLiveHashHandler;
    @Mock private CryptoGetStakersHandler cryptoGetStakersHandler;
    @Mock private FileGetContentsHandler fileGetContentsHandler;
    @Mock private FileGetInfoHandler fileGetInfoHandler;
    @Mock private NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;
    @Mock private NetworkGetByKeyHandler networkGetByKeyHandler;
    @Mock private NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler;
    @Mock private NetworkGetVersionInfoHandler networkGetVersionInfoHandler;
    @Mock private NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;
    @Mock private NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;
    @Mock private ScheduleGetInfoHandler scheduleGetInfoHandler;
    @Mock private TokenGetInfoHandler tokenGetInfoHandler;
    @Mock private TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler;
    @Mock private TokenGetNftInfoHandler tokenGetNftInfoHandler;
    @Mock private TokenGetNftInfosHandler tokenGetNftInfosHandler;

    private QueryHandlers handlers;

    private QueryDispatcher dispatcher;

    @BeforeEach
    void setup() {
        handlers =
                new QueryHandlers(
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
        assertThatThrownBy(() -> new QueryDispatcher(null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testGetHandlerWithIllegalParameters() {
        assertThatThrownBy(() -> dispatcher.getHandler(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetHandlersWithNoQuerySet() {
        // given
        final var query = Query.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.getHandler(query))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testGetHandler(final Query query, final Function<QueryHandlers, QueryHandler> getter) {
        // when
        final var result = dispatcher.getHandler(query);

        // then
        assertThat(result).isEqualTo(getter.apply(handlers));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchValidateWithIllegalParameters(@Mock final HederaState state) {
        // given
        final var query = Query.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchValidate(null, query))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchValidate(state, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDispatchValidateWithNoQuerySet(@Mock final HederaState state) {
        // given
        final var query = Query.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchValidate(state, query))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testValidate(
            final Query query,
            final Function<QueryHandlers, QueryHandler> ignore,
            final Verification verifyValidate)
            throws PreCheckException {
        // given
        final var state = mock(HederaState.class);

        // when
        dispatcher.dispatchValidate(state, query);

        // then
        verifyValidate.verify(handlers);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchFindResponseWithIllegalParameters(@Mock final HederaState state) {
        // given
        final var query = Query.newBuilder().build();
        final var header = ResponseHeader.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchFindResponse(null, query, header))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchFindResponse(state, null, header))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchFindResponse(state, query, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDispatchFindResponseWithNoQuerySet(@Mock final HederaState state) {
        // given
        final var query = Query.newBuilder().build();
        final var header = ResponseHeader.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchFindResponse(state, query, header))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testFindResponse(
            final Query query,
            final Function<QueryHandlers, QueryHandler> ignore1,
            final Verification ignore,
            final Verification verifyFindResponse)
            throws PreCheckException {
        // given
        final var state = mock(HederaState.class);
        final var header = ResponseHeader.newBuilder().build();

        // when
        dispatcher.dispatchFindResponse(state, query, header);

        // then
        verifyFindResponse.verify(handlers);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                Arguments.of(
                        Query.newBuilder()
                                .setConsensusGetTopicInfo(
                                        ConsensusGetTopicInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::consensusGetTopicInfoHandler,
                        (Verification)
                                h -> verify(h.consensusGetTopicInfoHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.consensusGetTopicInfoHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setGetBySolidityID(GetBySolidityIDQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::contractGetBySolidityIDHandler,
                        (Verification)
                                h -> verify(h.contractGetBySolidityIDHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.contractGetBySolidityIDHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setContractCallLocal(ContractCallLocalQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::contractCallLocalHandler,
                        (Verification) h -> verify(h.contractCallLocalHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.contractCallLocalHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setContractGetInfo(ContractGetInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::contractGetInfoHandler,
                        (Verification) h -> verify(h.contractGetInfoHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.contractGetInfoHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setContractGetBytecode(
                                        ContractGetBytecodeQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::contractGetBytecodeHandler,
                        (Verification) h -> verify(h.contractGetBytecodeHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.contractGetBytecodeHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setContractGetRecords(ContractGetRecordsQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::contractGetRecordsHandler,
                        (Verification) h -> verify(h.contractGetRecordsHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.contractGetRecordsHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setCryptogetAccountBalance(
                                        CryptoGetAccountBalanceQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::cryptoGetAccountBalanceHandler,
                        (Verification)
                                h -> verify(h.cryptoGetAccountBalanceHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.cryptoGetAccountBalanceHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setCryptoGetInfo(CryptoGetInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::cryptoGetAccountInfoHandler,
                        (Verification) h -> verify(h.cryptoGetAccountInfoHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.cryptoGetAccountInfoHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setCryptoGetAccountRecords(
                                        CryptoGetAccountRecordsQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::cryptoGetAccountRecordsHandler,
                        (Verification)
                                h -> verify(h.cryptoGetAccountRecordsHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.cryptoGetAccountRecordsHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setCryptoGetLiveHash(CryptoGetLiveHashQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::cryptoGetLiveHashHandler,
                        (Verification) h -> verify(h.cryptoGetLiveHashHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.cryptoGetLiveHashHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setCryptoGetProxyStakers(
                                        CryptoGetStakersQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::cryptoGetStakersHandler,
                        (Verification) h -> verify(h.cryptoGetStakersHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.cryptoGetStakersHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setFileGetContents(FileGetContentsQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::fileGetContentsHandler,
                        (Verification) h -> verify(h.fileGetContentsHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.fileGetContentsHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setFileGetInfo(FileGetInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::fileGetInfoHandler,
                        (Verification) h -> verify(h.fileGetInfoHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.fileGetInfoHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setScheduleGetInfo(ScheduleGetInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::scheduleGetInfoHandler,
                        (Verification) h -> verify(h.scheduleGetInfoHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.scheduleGetInfoHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setTokenGetInfo(TokenGetInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetInfoHandler,
                        (Verification) h -> verify(h.tokenGetInfoHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.tokenGetInfoHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setTokenGetAccountNftInfos(
                                        TokenGetAccountNftInfosQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::tokenGetAccountNftInfosHandler,
                        (Verification)
                                h -> verify(h.tokenGetAccountNftInfosHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.tokenGetAccountNftInfosHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setTokenGetNftInfo(TokenGetNftInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::tokenGetNftInfoHandler,
                        (Verification) h -> verify(h.tokenGetNftInfoHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.tokenGetNftInfoHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setTokenGetNftInfos(TokenGetNftInfosQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::tokenGetNftInfosHandler,
                        (Verification) h -> verify(h.tokenGetNftInfosHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.tokenGetNftInfosHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setAccountDetails(GetAccountDetailsQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::networkGetAccountDetailsHandler,
                        (Verification) h -> verify(h.networkGetAccountDetailsHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.networkGetAccountDetailsHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setNetworkGetVersionInfo(
                                        NetworkGetVersionInfoQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::networkGetVersionInfoHandler,
                        (Verification) h -> verify(h.networkGetVersionInfoHandler()).validate(any()),
                        (Verification)
                                h -> verify(h.networkGetVersionInfoHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder().setGetByKey(GetByKeyQuery.getDefaultInstance()).build(),
                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetByKeyHandler,
                        (Verification) h -> verify(h.networkGetByKeyHandler()).validate(any()),
                        (Verification) h -> verify(h.networkGetByKeyHandler()).findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setNetworkGetExecutionTime(
                                        NetworkGetExecutionTimeQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::networkGetExecutionTimeHandler,
                        (Verification)
                                h -> verify(h.networkGetExecutionTimeHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.networkGetExecutionTimeHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setTransactionGetReceipt(
                                        TransactionGetReceiptQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::networkTransactionGetReceiptHandler,
                        (Verification)
                                h -> verify(h.networkTransactionGetReceiptHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.networkTransactionGetReceiptHandler())
                                                .findResponse(any(), any())),
                Arguments.of(
                        Query.newBuilder()
                                .setTransactionGetRecord(
                                        TransactionGetRecordQuery.getDefaultInstance())
                                .build(),
                        (Function<QueryHandlers, QueryHandler>)
                                QueryHandlers::networkTransactionGetRecordHandler,
                        (Verification) h -> verify(h.networkTransactionGetRecordHandler()).validate(any()),
                        (Verification)
                                h ->
                                        verify(h.networkTransactionGetRecordHandler())
                                                .findResponse(any(), any())));
    }

    @FunctionalInterface
    private interface Verification {
        void verify(QueryHandlers handlers) throws PreCheckException;
    }
}
