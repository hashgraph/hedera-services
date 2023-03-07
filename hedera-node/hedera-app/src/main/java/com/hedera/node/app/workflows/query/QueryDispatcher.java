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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
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
    private static final String GET_FAST_RECORD_IS_NOT_SUPPORTED = "TransactionGetFastRecord is not supported";

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
        return switch (query.query().kind()) {
            case CONSENSUS_GET_TOPIC_INFO -> handlers.consensusGetTopicInfoHandler();

            case GET_BY_SOLIDITY_ID -> handlers.contractGetBySolidityIDHandler();
            case CONTRACT_CALL_LOCAL -> handlers.contractCallLocalHandler();
            case CONTRACT_GET_INFO -> handlers.contractGetInfoHandler();
            case CONTRACT_GET_BYTECODE -> handlers.contractGetBytecodeHandler();
            case CONTRACT_GET_RECORDS -> handlers.contractGetRecordsHandler();

            case CRYPTOGET_ACCOUNT_BALANCE -> handlers.cryptoGetAccountBalanceHandler();
            case CRYPTO_GET_INFO -> handlers.cryptoGetAccountInfoHandler();
            case CRYPTO_GET_ACCOUNT_RECORDS -> handlers.cryptoGetAccountRecordsHandler();
            case CRYPTO_GET_LIVE_HASH -> handlers.cryptoGetLiveHashHandler();
            case CRYPTO_GET_PROXY_STAKERS -> handlers.cryptoGetStakersHandler();

            case FILE_GET_CONTENTS -> handlers.fileGetContentsHandler();
            case FILE_GET_INFO -> handlers.fileGetInfoHandler();

            case ACCOUNT_DETAILS -> handlers.networkGetAccountDetailsHandler();
            case GET_BY_KEY -> handlers.networkGetByKeyHandler();
            case NETWORK_GET_VERSION_INFO -> handlers.networkGetVersionInfoHandler();
            case NETWORK_GET_EXECUTION_TIME -> handlers.networkGetExecutionTimeHandler();
            case TRANSACTION_GET_RECEIPT -> handlers.networkTransactionGetReceiptHandler();
            case TRANSACTION_GET_RECORD -> handlers.networkTransactionGetRecordHandler();

            case SCHEDULE_GET_INFO -> handlers.scheduleGetInfoHandler();

            case TOKEN_GET_INFO -> handlers.tokenGetInfoHandler();
            case TOKEN_GET_ACCOUNT_NFT_INFOS -> handlers.tokenGetAccountNftInfosHandler();
            case TOKEN_GET_NFT_INFO -> handlers.tokenGetNftInfoHandler();
            case TOKEN_GET_NFT_INFOS -> handlers.tokenGetNftInfosHandler();

            case TRANSACTION_GET_FAST_RECORD -> throw new UnsupportedOperationException(GET_FAST_RECORD_IS_NOT_SUPPORTED);
            case UNSET -> throw new UnsupportedOperationException(QUERY_NOT_SET);
        };
    }

    /**
     * Validates the query by dispatching the query to its specific handlers.
     *
     * @param storeFactory the {@link ReadableStoreFactory} that keeps all stores which are eventually
     *                     needed
     * @param query        the {@link Query} of the request
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public ResponseCodeEnum validate(@NonNull final ReadableStoreFactory storeFactory, @NonNull final Query query)
            throws PreCheckException {
        requireNonNull(storeFactory);
        requireNonNull(query);

        return switch (query.query().kind()) {
            case CONSENSUS_GET_TOPIC_INFO -> handlers.consensusGetTopicInfoHandler()
                    .validate(query, storeFactory.createTopicStore());

            case GET_BY_SOLIDITY_ID -> handlers.contractGetBySolidityIDHandler().validate(query);
            case CONTRACT_CALL_LOCAL -> handlers.contractCallLocalHandler().validate(query);
            case CONTRACT_GET_INFO -> handlers.contractGetInfoHandler().validate(query);
            case CONTRACT_GET_BYTECODE -> handlers.contractGetBytecodeHandler().validate(query);
            case CONTRACT_GET_RECORDS -> handlers.contractGetRecordsHandler().validate(query);

            case CRYPTOGET_ACCOUNT_BALANCE -> handlers.cryptoGetAccountBalanceHandler()
                    .validate(query);
            case CRYPTO_GET_INFO -> handlers.cryptoGetAccountInfoHandler().validate(query);
            case CRYPTO_GET_ACCOUNT_RECORDS -> handlers.cryptoGetAccountRecordsHandler()
                    .validate(query);
            case CRYPTO_GET_LIVE_HASH -> handlers.cryptoGetLiveHashHandler().validate(query);
            case CRYPTO_GET_PROXY_STAKERS -> handlers.cryptoGetStakersHandler().validate(query);

            case FILE_GET_CONTENTS -> handlers.fileGetContentsHandler().validate(query);
            case FILE_GET_INFO -> handlers.fileGetInfoHandler().validate(query);

            case ACCOUNT_DETAILS -> handlers.networkGetAccountDetailsHandler().validate(query);
            case GET_BY_KEY -> handlers.networkGetByKeyHandler().validate(query);
            case NETWORK_GET_VERSION_INFO -> handlers.networkGetVersionInfoHandler()
                    .validate(query);
            case NETWORK_GET_EXECUTION_TIME -> handlers.networkGetExecutionTimeHandler()
                    .validate(query);
            case TRANSACTION_GET_RECEIPT -> handlers.networkTransactionGetReceiptHandler()
                    .validate(query);
            case TRANSACTION_GET_RECORD -> handlers.networkTransactionGetRecordHandler()
                    .validate(query);

            case SCHEDULE_GET_INFO -> handlers.scheduleGetInfoHandler().validate(query);

            case TOKEN_GET_INFO -> handlers.tokenGetInfoHandler().validate(query);
            case TOKEN_GET_ACCOUNT_NFT_INFOS -> handlers.tokenGetAccountNftInfosHandler()
                    .validate(query);
            case TOKEN_GET_NFT_INFO -> handlers.tokenGetNftInfoHandler().validate(query);
            case TOKEN_GET_NFT_INFOS -> handlers.tokenGetNftInfosHandler().validate(query);

            case TRANSACTION_GET_FAST_RECORD -> throw new UnsupportedOperationException(GET_FAST_RECORD_IS_NOT_SUPPORTED);
            case UNSET -> throw new UnsupportedOperationException(QUERY_NOT_SET);
        };
    }

    /**
     * Gets the response for a given query by dispatching its respective handlers.
     *
     * @param storeFactory the {@link ReadableStoreFactory} that keeps all stores which are eventually needed
     * @param query the actual {@link Query}
     * @param header the {@link ResponseHeader} that should be used in the response, if it is successful
     * @param queryContext
     * @return the {@link Response} with the requested answer
     */
    public Response getResponse(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final Query query,
            @NonNull final ResponseHeader header,
            @NonNull final QueryContext queryContext) {
        requireNonNull(storeFactory);
        requireNonNull(query);
        requireNonNull(header);
        requireNonNull(queryContext);

        return switch (query.query().kind()) {
            case CONSENSUS_GET_TOPIC_INFO -> handlers.consensusGetTopicInfoHandler()
                    .findResponse(query, header, storeFactory.createTopicStore(), queryContext);

            case GET_BY_SOLIDITY_ID -> handlers.contractGetBySolidityIDHandler().findResponse(query, header);
            case CONTRACT_CALL_LOCAL -> handlers.contractCallLocalHandler().findResponse(query, header);
            case CONTRACT_GET_INFO -> handlers.contractGetInfoHandler().findResponse(query, header);
            case CONTRACT_GET_BYTECODE -> handlers.contractGetBytecodeHandler().findResponse(query, header);
            case CONTRACT_GET_RECORDS -> handlers.contractGetRecordsHandler().findResponse(query, header);

            case CRYPTOGET_ACCOUNT_BALANCE -> handlers.cryptoGetAccountBalanceHandler()
                    .findResponse(query, header);
            case CRYPTO_GET_INFO -> handlers.cryptoGetAccountInfoHandler().findResponse(query, header);
            case CRYPTO_GET_ACCOUNT_RECORDS -> handlers.cryptoGetAccountRecordsHandler()
                    .findResponse(query, header);
            case CRYPTO_GET_LIVE_HASH -> handlers.cryptoGetLiveHashHandler().findResponse(query, header);
            case CRYPTO_GET_PROXY_STAKERS -> handlers.cryptoGetStakersHandler().findResponse(query, header);

            case FILE_GET_CONTENTS -> handlers.fileGetContentsHandler().findResponse(query, header);
            case FILE_GET_INFO -> handlers.fileGetInfoHandler().findResponse(query, header);

            case ACCOUNT_DETAILS -> handlers.networkGetAccountDetailsHandler().findResponse(query, header);
            case GET_BY_KEY -> handlers.networkGetByKeyHandler().findResponse(query, header);
            case NETWORK_GET_VERSION_INFO -> handlers.networkGetVersionInfoHandler()
                    .findResponse(query, header);
            case NETWORK_GET_EXECUTION_TIME -> handlers.networkGetExecutionTimeHandler()
                    .findResponse(query, header);
            case TRANSACTION_GET_RECEIPT -> handlers.networkTransactionGetReceiptHandler()
                    .findResponse(query, header);
            case TRANSACTION_GET_RECORD -> handlers.networkTransactionGetRecordHandler()
                    .findResponse(query, header);

            case SCHEDULE_GET_INFO -> handlers.scheduleGetInfoHandler().findResponse(query, header);

            case TOKEN_GET_INFO -> handlers.tokenGetInfoHandler().findResponse(query, header);
            case TOKEN_GET_ACCOUNT_NFT_INFOS -> handlers.tokenGetAccountNftInfosHandler()
                    .findResponse(query, header);
            case TOKEN_GET_NFT_INFO -> handlers.tokenGetNftInfoHandler().findResponse(query, header);
            case TOKEN_GET_NFT_INFOS -> handlers.tokenGetNftInfosHandler().findResponse(query, header);

            case TRANSACTION_GET_FAST_RECORD -> throw new UnsupportedOperationException(GET_FAST_RECORD_IS_NOT_SUPPORTED);
            case UNSET -> throw new UnsupportedOperationException(QUERY_NOT_SET);
        };
    }
}
