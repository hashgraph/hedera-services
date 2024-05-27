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

package com.hedera.services.bdd.junit.hedera.utils;

import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import edu.umd.cs.findbugs.annotations.NonNull;

public class GrpcUtils {
    private GrpcUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Sends a query to the requested node's gRPC service for the given functionality
     * using the given clients without TLS.
     *
     * @param query the query to send
     * @param clients the clients to use
     * @param functionality the functionality to query
     * @param nodeAccountId the node to send the query to
     * @return the response from the query
     */
    public static Response send(
            @NonNull final Query query,
            @NonNull final HapiClients clients,
            @NonNull final HederaFunctionality functionality,
            @NonNull final com.hederahashgraph.api.proto.java.AccountID nodeAccountId) {
        return switch (functionality) {
            case ConsensusGetTopicInfo -> clients.getConsSvcStub(nodeAccountId, false)
                    .getTopicInfo(query);
            case GetBySolidityID -> clients.getScSvcStub(nodeAccountId, false).getBySolidityID(query);
            case ContractCallLocal -> clients.getScSvcStub(nodeAccountId, false).contractCallLocalMethod(query);
            case ContractGetInfo -> clients.getScSvcStub(nodeAccountId, false).getContractInfo(query);
            case ContractGetBytecode -> clients.getScSvcStub(nodeAccountId, false)
                    .contractGetBytecode(query);
            case ContractGetRecords -> clients.getScSvcStub(nodeAccountId, false)
                    .getTxRecordByContractID(query);
            case CryptoGetAccountBalance -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .cryptoGetBalance(query);
            case CryptoGetAccountRecords -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .getAccountRecords(query);
            case CryptoGetInfo -> clients.getCryptoSvcStub(nodeAccountId, false).getAccountInfo(query);
            case CryptoGetLiveHash -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .getLiveHash(query);
            case FileGetContents -> clients.getFileSvcStub(nodeAccountId, false).getFileContent(query);
            case FileGetInfo -> clients.getFileSvcStub(nodeAccountId, false).getFileInfo(query);
            case TransactionGetReceipt -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .getTransactionReceipts(query);
            case TransactionGetRecord -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .getTxRecordByTxID(query);
            case GetVersionInfo -> clients.getNetworkSvcStub(nodeAccountId, false)
                    .getVersionInfo(query);
            case TokenGetInfo -> clients.getTokenSvcStub(nodeAccountId, false).getTokenInfo(query);
            case ScheduleGetInfo -> clients.getScheduleSvcStub(nodeAccountId, false)
                    .getScheduleInfo(query);
            case TokenGetNftInfo -> clients.getTokenSvcStub(nodeAccountId, false)
                    .getTokenNftInfo(query);
            case TokenGetNftInfos -> clients.getTokenSvcStub(nodeAccountId, false)
                    .getTokenNftInfos(query);
            case TokenGetAccountNftInfos -> clients.getTokenSvcStub(nodeAccountId, false)
                    .getAccountNftInfos(query);
            case NetworkGetExecutionTime -> clients.getNetworkSvcStub(nodeAccountId, false)
                    .getExecutionTime(query);
            case GetAccountDetails -> clients.getNetworkSvcStub(nodeAccountId, false)
                    .getAccountDetails(query);
            default -> throw new IllegalArgumentException(functionality + " is not a query");
        };
    }
}
