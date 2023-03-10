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

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryDispatcherTest {
    //    @Mock
    //    private ConsensusGetTopicInfoHandler consensusGetTopicInfoHandler;
    //
    //    @Mock
    //    private ContractGetBySolidityIDHandler contractGetBySolidityIDHandler;
    //
    //    @Mock
    //    private ContractCallLocalHandler contractCallLocalHandler;
    //
    //    @Mock
    //    private ContractGetInfoHandler contractGetInfoHandler;
    //
    //    @Mock
    //    private ContractGetBytecodeHandler contractGetBytecodeHandler;
    //
    //    @Mock
    //    private ContractGetRecordsHandler contractGetRecordsHandler;
    //
    //    @Mock
    //    private CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler;
    //
    //    @Mock
    //    private CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler;
    //
    //    @Mock
    //    private CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler;
    //
    //    @Mock
    //    private CryptoGetLiveHashHandler cryptoGetLiveHashHandler;
    //
    //    @Mock
    //    private CryptoGetStakersHandler cryptoGetStakersHandler;
    //
    //    @Mock
    //    private FileGetContentsHandler fileGetContentsHandler;
    //
    //    @Mock
    //    private FileGetInfoHandler fileGetInfoHandler;
    //
    //    @Mock
    //    private NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;
    //
    //    @Mock
    //    private NetworkGetByKeyHandler networkGetByKeyHandler;
    //
    //    @Mock
    //    private NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler;
    //
    //    @Mock
    //    private NetworkGetVersionInfoHandler networkGetVersionInfoHandler;
    //
    //    @Mock
    //    private NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;
    //
    //    @Mock
    //    private NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;
    //
    //    @Mock
    //    private ScheduleGetInfoHandler scheduleGetInfoHandler;
    //
    //    @Mock
    //    private TokenGetInfoHandler tokenGetInfoHandler;
    //
    //    @Mock
    //    private TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler;
    //
    //    @Mock
    //    private TokenGetNftInfoHandler tokenGetNftInfoHandler;
    //
    //    @Mock
    //    private TokenGetNftInfosHandler tokenGetNftInfosHandler;
    //
    //    @Mock
    //    private QueryContext queryContext;
    //
    //    private QueryHandlers handlers;
    //
    //    private QueryDispatcher dispatcher;
    //
    //    @BeforeEach
    //    void setup() {
    //        handlers = new QueryHandlers(
    //                consensusGetTopicInfoHandler,
    //                contractGetBySolidityIDHandler,
    //                contractCallLocalHandler,
    //                contractGetInfoHandler,
    //                contractGetBytecodeHandler,
    //                contractGetRecordsHandler,
    //                cryptoGetAccountBalanceHandler,
    //                cryptoGetAccountInfoHandler,
    //                cryptoGetAccountRecordsHandler,
    //                cryptoGetLiveHashHandler,
    //                cryptoGetStakersHandler,
    //                fileGetContentsHandler,
    //                fileGetInfoHandler,
    //                networkGetAccountDetailsHandler,
    //                networkGetByKeyHandler,
    //                networkGetExecutionTimeHandler,
    //                networkGetVersionInfoHandler,
    //                networkTransactionGetReceiptHandler,
    //                networkTransactionGetRecordHandler,
    //                scheduleGetInfoHandler,
    //                tokenGetInfoHandler,
    //                tokenGetAccountNftInfosHandler,
    //                tokenGetNftInfoHandler,
    //                tokenGetNftInfosHandler);
    //
    //        dispatcher = new QueryDispatcher(handlers);
    //    }
    //
    //    @SuppressWarnings("ConstantConditions")
    //    @Test
    //    void testConstructorWithIllegalParameters() {
    //        assertThatThrownBy(() -> new QueryDispatcher(null)).isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @SuppressWarnings("ConstantConditions")
    //    @Test
    //    void testGetHandlerWithIllegalParameters() {
    //        assertThatThrownBy(() -> dispatcher.getHandler(null)).isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @Test
    //    void testGetHandlersWithNoQuerySet() {
    //        // given
    //        final var query = Query.newBuilder().build();
    //
    //        // then
    //        assertThatThrownBy(() -> dispatcher.getHandler(query)).isInstanceOf(UnsupportedOperationException.class);
    //    }
    //
    //    @ParameterizedTest
    //    @MethodSource("getDispatchParameters")
    //    void testGetHandler(final Query query, final Function<QueryHandlers, QueryHandler> getter) {
    //        // when
    //        final var result = dispatcher.getHandler(query);
    //
    //        // then
    //        assertThat(result).isEqualTo(getter.apply(handlers));
    //    }
    //
    //    @SuppressWarnings("ConstantConditions")
    //    @Test
    //    void testDispatchValidateWithIllegalParameters(@Mock final StoreFactory storeFactory) {
    //        // given
    //        final var query = Query.newBuilder().build();
    //
    //        // then
    //        assertThatThrownBy(() -> dispatcher.validate(null, query)).isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(() -> dispatcher.validate(storeFactory,
    // null)).isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @Test
    //    void testDispatchValidateWithNoQuerySet(@Mock final StoreFactory storeFactory) {
    //        // given
    //        final var query = Query.newBuilder().build();
    //
    //        // then
    //        assertThatThrownBy(() -> dispatcher.validate(storeFactory, query))
    //                .isInstanceOf(UnsupportedOperationException.class);
    //    }
    //
    //    @ParameterizedTest
    //    @MethodSource("getDispatchParameters")
    //    void testValidate(
    //            final Query query, final Function<QueryHandlers, QueryHandler> ignore, final Verification
    // verifyValidate)
    //            throws PreCheckException {
    //        // given
    //        final var storeFactory = mock(StoreFactory.class);
    //
    //        // when
    //        dispatcher.validate(storeFactory, query);
    //
    //        // then
    //        verifyValidate.verify(handlers);
    //    }
    //
    //    @SuppressWarnings("ConstantConditions")
    //    @Test
    //    void testDispatchFindResponseWithIllegalParameters(@Mock final StoreFactory storeFactory) {
    //        // given
    //        final var query = Query.newBuilder().build();
    //        final var header = ResponseHeader.newBuilder().build();
    //
    //        // then
    //        assertThatThrownBy(() -> dispatcher.getResponse(null, query, header, queryContext))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(() -> dispatcher.getResponse(storeFactory, null, header, queryContext))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(() -> dispatcher.getResponse(storeFactory, query, null, queryContext))
    //                .isInstanceOf(NullPointerException.class);
    //        assertThatThrownBy(() -> dispatcher.getResponse(storeFactory, query, header, null))
    //                .isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @Test
    //    void testDispatchFindResponseWithNoQuerySet(@Mock final StoreFactory storeFactory) {
    //        // given
    //        final var query = Query.newBuilder().build();
    //        final var header = ResponseHeader.newBuilder().build();
    //
    //        // then
    //        assertThatThrownBy(() -> dispatcher.getResponse(storeFactory, query, header, queryContext))
    //                .isInstanceOf(UnsupportedOperationException.class);
    //    }
    //
    //    @ParameterizedTest
    //    @MethodSource("getDispatchParameters")
    //    void testFindResponse(
    //            final Query query,
    //            final Function<QueryHandlers, QueryHandler> ignore1,
    //            final Verification ignore,
    //            final Verification verifyFindResponse)
    //            throws PreCheckException {
    //        // given
    //        final var storeFactory = mock(StoreFactory.class);
    //        final var header = ResponseHeader.newBuilder().build();
    //
    //        // when
    //        dispatcher.getResponse(storeFactory, query, header, queryContext);
    //
    //        // then
    //        verifyFindResponse.verify(handlers);
    //    }
    //
    //    private static Stream<Arguments> getDispatchParameters() {
    //        return Stream.of(
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .consensusGetTopicInfo(
    //                                        ConsensusGetTopicInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::consensusGetTopicInfoHandler,
    //                        (Verification)
    //                                h -> verify(h.consensusGetTopicInfoHandler()).validate(any(), any()),
    //                        (Verification)
    //                                h -> verify(h.consensusGetTopicInfoHandler()).findResponse(any(), any(), any(),
    // any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .getBySolidityID(
    //                                        GetBySolidityIDQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetBySolidityIDHandler,
    //                        (Verification)
    //                                h -> verify(h.contractGetBySolidityIDHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.contractGetBySolidityIDHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .contractCallLocal(
    //                                        ContractCallLocalQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractCallLocalHandler,
    //                        (Verification) h -> verify(h.contractCallLocalHandler()).validate(any()),
    //                        (Verification) h -> verify(h.contractCallLocalHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .contractGetInfo(
    //                                        ContractGetInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetInfoHandler,
    //                        (Verification) h -> verify(h.contractGetInfoHandler()).validate(any()),
    //                        (Verification) h -> verify(h.contractGetInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .contractGetBytecode(
    //                                        ContractGetBytecodeQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetBytecodeHandler,
    //                        (Verification)
    //                                h -> verify(h.contractGetBytecodeHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.contractGetBytecodeHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .contractGetRecords(
    //                                        ContractGetRecordsQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::contractGetRecordsHandler,
    //                        (Verification)
    //                                h -> verify(h.contractGetRecordsHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.contractGetRecordsHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .cryptogetAccountBalance(CryptoGetAccountBalanceQuery.newBuilder()
    //                                        .build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetAccountBalanceHandler,
    //                        (Verification)
    //                                h -> verify(h.cryptoGetAccountBalanceHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.cryptoGetAccountBalanceHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .cryptoGetInfo(CryptoGetInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetAccountInfoHandler,
    //                        (Verification)
    //                                h -> verify(h.cryptoGetAccountInfoHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.cryptoGetAccountInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .cryptoGetAccountRecords(CryptoGetAccountRecordsQuery.newBuilder()
    //                                        .build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetAccountRecordsHandler,
    //                        (Verification)
    //                                h -> verify(h.cryptoGetAccountRecordsHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.cryptoGetAccountRecordsHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .cryptoGetLiveHash(
    //                                        CryptoGetLiveHashQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetLiveHashHandler,
    //                        (Verification) h -> verify(h.cryptoGetLiveHashHandler()).validate(any()),
    //                        (Verification) h -> verify(h.cryptoGetLiveHashHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .cryptoGetProxyStakers(
    //                                        CryptoGetStakersQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::cryptoGetStakersHandler,
    //                        (Verification) h -> verify(h.cryptoGetStakersHandler()).validate(any()),
    //                        (Verification) h -> verify(h.cryptoGetStakersHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .fileGetContents(
    //                                        FileGetContentsQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::fileGetContentsHandler,
    //                        (Verification) h -> verify(h.fileGetContentsHandler()).validate(any()),
    //                        (Verification) h -> verify(h.fileGetContentsHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .fileGetInfo(FileGetInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::fileGetInfoHandler,
    //                        (Verification) h -> verify(h.fileGetInfoHandler()).validate(any()),
    //                        (Verification) h -> verify(h.fileGetInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .scheduleGetInfo(
    //                                        ScheduleGetInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::scheduleGetInfoHandler,
    //                        (Verification) h -> verify(h.scheduleGetInfoHandler()).validate(any()),
    //                        (Verification) h -> verify(h.scheduleGetInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .tokenGetInfo(TokenGetInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetInfoHandler,
    //                        (Verification) h -> verify(h.tokenGetInfoHandler()).validate(any()),
    //                        (Verification) h -> verify(h.tokenGetInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .tokenGetAccountNftInfos(TokenGetAccountNftInfosQuery.newBuilder()
    //                                        .build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetAccountNftInfosHandler,
    //                        (Verification)
    //                                h -> verify(h.tokenGetAccountNftInfosHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.tokenGetAccountNftInfosHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .tokenGetNftInfo(
    //                                        TokenGetNftInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetNftInfoHandler,
    //                        (Verification) h -> verify(h.tokenGetNftInfoHandler()).validate(any()),
    //                        (Verification) h -> verify(h.tokenGetNftInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .tokenGetNftInfos(
    //                                        TokenGetNftInfosQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::tokenGetNftInfosHandler,
    //                        (Verification) h -> verify(h.tokenGetNftInfosHandler()).validate(any()),
    //                        (Verification) h -> verify(h.tokenGetNftInfosHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .accountDetails(
    //                                        GetAccountDetailsQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetAccountDetailsHandler,
    //                        (Verification)
    //                                h -> verify(h.networkGetAccountDetailsHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.networkGetAccountDetailsHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .networkGetVersionInfo(
    //                                        NetworkGetVersionInfoQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetVersionInfoHandler,
    //                        (Verification)
    //                                h -> verify(h.networkGetVersionInfoHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.networkGetVersionInfoHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .getByKey(GetByKeyQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetByKeyHandler,
    //                        (Verification) h -> verify(h.networkGetByKeyHandler()).validate(any()),
    //                        (Verification) h -> verify(h.networkGetByKeyHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .networkGetExecutionTime(NetworkGetExecutionTimeQuery.newBuilder()
    //                                        .build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkGetExecutionTimeHandler,
    //                        (Verification)
    //                                h -> verify(h.networkGetExecutionTimeHandler()).validate(any()),
    //                        (Verification)
    //                                h -> verify(h.networkGetExecutionTimeHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .transactionGetReceipt(
    //                                        TransactionGetReceiptQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>)
    // QueryHandlers::networkTransactionGetReceiptHandler,
    //                        (Verification) h ->
    //                                verify(h.networkTransactionGetReceiptHandler()).validate(any()),
    //                        (Verification) h ->
    //                                verify(h.networkTransactionGetReceiptHandler()).findResponse(any(), any())),
    //                Arguments.of(
    //                        Query.newBuilder()
    //                                .transactionGetRecord(
    //                                        TransactionGetRecordQuery.newBuilder().build())
    //                                .build(),
    //                        (Function<QueryHandlers, QueryHandler>) QueryHandlers::networkTransactionGetRecordHandler,
    //                        (Verification) h ->
    //                                verify(h.networkTransactionGetRecordHandler()).validate(any()),
    //                        (Verification) h ->
    //                                verify(h.networkTransactionGetRecordHandler()).findResponse(any(), any())));
    //    }
    //
    //    @FunctionalInterface
    //    private interface Verification {
    //        void verify(QueryHandlers handlers) throws PreCheckException;
    //    }
}
