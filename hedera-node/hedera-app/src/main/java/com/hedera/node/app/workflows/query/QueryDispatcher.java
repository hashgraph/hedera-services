/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@code QueryDispatcher} provides functionality to forward validate, and reply-query requests to
 * the appropriate handler
 */
@Singleton
public class QueryDispatcher {

    private static final String QUERY_NOT_SET = "Query not set";
    private static final String GET_FAST_RECORD_IS_NOT_SUPPORTED =
            "TransactionGetFastRecord is not supported";

    private final QueryHandlers handlers;

    /**
     * Constructor of {@code QueryDispatcher}
     *
     * @param handlers a {@link QueryHandlers} record with all available handlers
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    @Inject
    public QueryDispatcher(@NonNull final QueryHandlers handlers) {
        this.handlers = requireNonNull(handlers);
    }

    /**
     * Returns the {@link QueryHandler} for a given {@link Query}
     *
     * @param query the {@link Query} for which the {@link QueryHandler} is requested
     * @return the {@code QueryHandler} for the query
     */
    @NonNull
    public QueryHandler getHandler(@NonNull final Query query) {
        return switch (query.getQueryCase()) {
            case CONSENSUSGETTOPICINFO -> handlers.consensusGetTopicInfoHandler();

            case GETBYSOLIDITYID -> handlers.contractGetBySolidityIDHandler();
            case CONTRACTCALLLOCAL -> handlers.contractCallLocalHandler();
            case CONTRACTGETINFO -> handlers.contractGetInfoHandler();
            case CONTRACTGETBYTECODE -> handlers.contractGetBytecodeHandler();
            case CONTRACTGETRECORDS -> handlers.contractGetRecordsHandler();

            case CRYPTOGETACCOUNTBALANCE -> handlers.cryptoGetAccountBalanceHandler();
            case CRYPTOGETINFO -> handlers.cryptoGetAccountInfoHandler();
            case CRYPTOGETACCOUNTRECORDS -> handlers.cryptoGetAccountRecordsHandler();
            case CRYPTOGETLIVEHASH -> handlers.cryptoGetLiveHashHandler();
            case CRYPTOGETPROXYSTAKERS -> handlers.cryptoGetStakersHandler();

            case FILEGETCONTENTS -> handlers.fileGetContentsHandler();
            case FILEGETINFO -> handlers.fileGetInfoHandler();

            case ACCOUNTDETAILS -> handlers.networkGetAccountDetailsHandler();
            case GETBYKEY -> handlers.networkGetByKeyHandler();
            case NETWORKGETVERSIONINFO -> handlers.networkGetVersionInfoHandler();
            case NETWORKGETEXECUTIONTIME -> handlers.networkGetExecutionTimeHandler();
            case TRANSACTIONGETRECEIPT -> handlers.networkTransactionGetReceiptHandler();
            case TRANSACTIONGETRECORD -> handlers.networkTransactionGetRecordHandler();

            case SCHEDULEGETINFO -> handlers.scheduleGetInfoHandler();

            case TOKENGETINFO -> handlers.tokenGetInfoHandler();
            case TOKENGETACCOUNTNFTINFOS -> handlers.tokenGetAccountNftInfosHandler();
            case TOKENGETNFTINFO -> handlers.tokenGetNftInfoHandler();
            case TOKENGETNFTINFOS -> handlers.tokenGetNftInfosHandler();

            case TRANSACTIONGETFASTRECORD -> throw new UnsupportedOperationException(
                    GET_FAST_RECORD_IS_NOT_SUPPORTED);
            case QUERY_NOT_SET -> throw new UnsupportedOperationException(QUERY_NOT_SET);
        };
    }

    /**
     * Validates the query by dispatching the query to its specific handlers.
     *
     * @param state the {@link HederaState} of this request
     * @param query the {@link Query} of the request
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public ResponseCodeEnum validate(@NonNull final HederaState state, @NonNull final Query query)
            throws PreCheckException {
        requireNonNull(state);
        requireNonNull(query);

        return switch (query.getQueryCase()) {
            case CONSENSUSGETTOPICINFO -> handlers.consensusGetTopicInfoHandler()
                    .validate(query, new ReadableTopicStore(state.createReadableStates("TOPICS")));

            case GETBYSOLIDITYID -> handlers.contractGetBySolidityIDHandler().validate(query);
            case CONTRACTCALLLOCAL -> handlers.contractCallLocalHandler().validate(query);
            case CONTRACTGETINFO -> handlers.contractGetInfoHandler().validate(query);
            case CONTRACTGETBYTECODE -> handlers.contractGetBytecodeHandler().validate(query);
            case CONTRACTGETRECORDS -> handlers.contractGetRecordsHandler().validate(query);

            case CRYPTOGETACCOUNTBALANCE -> handlers.cryptoGetAccountBalanceHandler()
                    .validate(query);
            case CRYPTOGETINFO -> handlers.cryptoGetAccountInfoHandler().validate(query);
            case CRYPTOGETACCOUNTRECORDS -> handlers.cryptoGetAccountRecordsHandler()
                    .validate(query);
            case CRYPTOGETLIVEHASH -> handlers.cryptoGetLiveHashHandler().validate(query);
            case CRYPTOGETPROXYSTAKERS -> handlers.cryptoGetStakersHandler().validate(query);

            case FILEGETCONTENTS -> handlers.fileGetContentsHandler().validate(query);
            case FILEGETINFO -> handlers.fileGetInfoHandler().validate(query);

            case ACCOUNTDETAILS -> handlers.networkGetAccountDetailsHandler().validate(query);
            case GETBYKEY -> handlers.networkGetByKeyHandler().validate(query);
            case NETWORKGETVERSIONINFO -> handlers.networkGetVersionInfoHandler().validate(query);
            case NETWORKGETEXECUTIONTIME -> handlers.networkGetExecutionTimeHandler()
                    .validate(query);
            case TRANSACTIONGETRECEIPT -> handlers.networkTransactionGetReceiptHandler()
                    .validate(query);
            case TRANSACTIONGETRECORD -> handlers.networkTransactionGetRecordHandler()
                    .validate(query);

            case SCHEDULEGETINFO -> handlers.scheduleGetInfoHandler().validate(query);

            case TOKENGETINFO -> handlers.tokenGetInfoHandler().validate(query);
            case TOKENGETACCOUNTNFTINFOS -> handlers.tokenGetAccountNftInfosHandler()
                    .validate(query);
            case TOKENGETNFTINFO -> handlers.tokenGetNftInfoHandler().validate(query);
            case TOKENGETNFTINFOS -> handlers.tokenGetNftInfosHandler().validate(query);

            case TRANSACTIONGETFASTRECORD -> throw new UnsupportedOperationException(
                    GET_FAST_RECORD_IS_NOT_SUPPORTED);
            case QUERY_NOT_SET -> throw new UnsupportedOperationException(QUERY_NOT_SET);

            default -> throw new UnsupportedOperationException(
                    "This type of query is not supported: " + query.getQueryCase());
        };
    }

    /**
     * Gets the response for a given query by dispatching its respective handlers.
     *
     * @param state the {@link HederaState} that should be used for the request
     * @param query the actual {@link Query}
     * @param header the {@link ResponseHeader} that should be used in the response, if it is
     *     successful
     * @return the {@link Response} with the requested answer
     */
    public Response getResponse(
            @NonNull final HederaState state,
            @NonNull final Query query,
            @NonNull final ResponseHeader header) {
        requireNonNull(state);
        requireNonNull(query);
        requireNonNull(header);

        return switch (query.getQueryCase()) {
            case CONSENSUSGETTOPICINFO -> handlers.consensusGetTopicInfoHandler()
                    .findResponse(
                            query,
                            header,
                            new ReadableTopicStore(state.createReadableStates("TOPICS")));

            case GETBYSOLIDITYID -> handlers.contractGetBySolidityIDHandler()
                    .findResponse(query, header);
            case CONTRACTCALLLOCAL -> handlers.contractCallLocalHandler()
                    .findResponse(query, header);
            case CONTRACTGETINFO -> handlers.contractGetInfoHandler().findResponse(query, header);
            case CONTRACTGETBYTECODE -> handlers.contractGetBytecodeHandler()
                    .findResponse(query, header);
            case CONTRACTGETRECORDS -> handlers.contractGetRecordsHandler()
                    .findResponse(query, header);

            case CRYPTOGETACCOUNTBALANCE -> handlers.cryptoGetAccountBalanceHandler()
                    .findResponse(query, header);
            case CRYPTOGETINFO -> handlers.cryptoGetAccountInfoHandler()
                    .findResponse(query, header);
            case CRYPTOGETACCOUNTRECORDS -> handlers.cryptoGetAccountRecordsHandler()
                    .findResponse(query, header);
            case CRYPTOGETLIVEHASH -> handlers.cryptoGetLiveHashHandler()
                    .findResponse(query, header);
            case CRYPTOGETPROXYSTAKERS -> handlers.cryptoGetStakersHandler()
                    .findResponse(query, header);

            case FILEGETCONTENTS -> handlers.fileGetContentsHandler().findResponse(query, header);
            case FILEGETINFO -> handlers.fileGetInfoHandler().findResponse(query, header);

            case ACCOUNTDETAILS -> handlers.networkGetAccountDetailsHandler()
                    .findResponse(query, header);
            case GETBYKEY -> handlers.networkGetByKeyHandler().findResponse(query, header);
            case NETWORKGETVERSIONINFO -> handlers.networkGetVersionInfoHandler()
                    .findResponse(query, header);
            case NETWORKGETEXECUTIONTIME -> handlers.networkGetExecutionTimeHandler()
                    .findResponse(query, header);
            case TRANSACTIONGETRECEIPT -> handlers.networkTransactionGetReceiptHandler()
                    .findResponse(query, header);
            case TRANSACTIONGETRECORD -> handlers.networkTransactionGetRecordHandler()
                    .findResponse(query, header);

            case SCHEDULEGETINFO -> handlers.scheduleGetInfoHandler().findResponse(query, header);

            case TOKENGETINFO -> handlers.tokenGetInfoHandler().findResponse(query, header);
            case TOKENGETACCOUNTNFTINFOS -> handlers.tokenGetAccountNftInfosHandler()
                    .findResponse(query, header);
            case TOKENGETNFTINFO -> handlers.tokenGetNftInfoHandler().findResponse(query, header);
            case TOKENGETNFTINFOS -> handlers.tokenGetNftInfosHandler().findResponse(query, header);

            case TRANSACTIONGETFASTRECORD -> throw new UnsupportedOperationException(
                    GET_FAST_RECORD_IS_NOT_SUPPORTED);
            case QUERY_NOT_SET -> throw new UnsupportedOperationException(QUERY_NOT_SET);
        };
    }
}
