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

import com.hedera.services.bdd.junit.hedera.SystemFunctionalityTarget;
import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
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
            case CryptoGetStakers -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .getStakersByAccountID(query);
            case TransactionGetFastRecord -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .getFastTransactionRecord(query);
            default -> throw new IllegalArgumentException(functionality + " is not a query");
        };
    }

    /**
     * Submits a transaction to the requested node's gRPC service for the given functionality
     * using the given clients without TLS.
     *
     * @param transaction the transaction to submit
     * @param clients the clients to use
     * @param functionality the functionality to query
     * @param target the target to use, given a system functionality
     * @param nodeAccountId the node to send the query to
     * @return the response from the query
     */
    public static TransactionResponse submit(
            @NonNull final Transaction transaction,
            @NonNull final HapiClients clients,
            @NonNull final HederaFunctionality functionality,
            @NonNull final SystemFunctionalityTarget target,
            @NonNull final com.hederahashgraph.api.proto.java.AccountID nodeAccountId) {
        return switch (functionality) {
            case ContractCall -> clients.getScSvcStub(nodeAccountId, false).contractCallMethod(transaction);
            case ContractCreate -> clients.getScSvcStub(nodeAccountId, false).createContract(transaction);
            case ContractUpdate -> clients.getScSvcStub(nodeAccountId, false).updateContract(transaction);
            case ContractDelete -> clients.getScSvcStub(nodeAccountId, false).deleteContract(transaction);
            case EthereumTransaction -> clients.getScSvcStub(nodeAccountId, false)
                    .callEthereum(transaction);
            case CryptoAddLiveHash -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .addLiveHash(transaction);
            case CryptoApproveAllowance -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .approveAllowances(transaction);
            case CryptoDeleteAllowance -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .deleteAllowances(transaction);
            case CryptoCreate -> clients.getCryptoSvcStub(nodeAccountId, false).createAccount(transaction);
            case CryptoDelete -> clients.getCryptoSvcStub(nodeAccountId, false).cryptoDelete(transaction);
            case CryptoDeleteLiveHash -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .deleteLiveHash(transaction);
            case CryptoTransfer -> clients.getCryptoSvcStub(nodeAccountId, false)
                    .cryptoTransfer(transaction);
            case CryptoUpdate -> clients.getCryptoSvcStub(nodeAccountId, false).updateAccount(transaction);
            case FileAppend -> clients.getFileSvcStub(nodeAccountId, false).appendContent(transaction);
            case FileCreate -> clients.getFileSvcStub(nodeAccountId, false).createFile(transaction);
            case FileDelete -> clients.getFileSvcStub(nodeAccountId, false).deleteFile(transaction);
            case FileUpdate -> clients.getFileSvcStub(nodeAccountId, false).updateFile(transaction);
            case SystemDelete -> switch (target) {
                case CONTRACT -> clients.getScSvcStub(nodeAccountId, false).systemDelete(transaction);
                case FILE -> clients.getFileSvcStub(nodeAccountId, false).systemDelete(transaction);
                case NA -> throw new IllegalArgumentException("SystemDelete target not available");
            };
            case SystemUndelete -> switch (target) {
                case CONTRACT -> clients.getScSvcStub(nodeAccountId, false).systemUndelete(transaction);
                case FILE -> clients.getFileSvcStub(nodeAccountId, false).systemUndelete(transaction);
                case NA -> throw new IllegalArgumentException("SystemUndelete target not available");
            };
            case Freeze -> clients.getFreezeSvcStub(nodeAccountId, false).freeze(transaction);
            case ConsensusCreateTopic -> clients.getConsSvcStub(nodeAccountId, false)
                    .createTopic(transaction);
            case ConsensusUpdateTopic -> clients.getConsSvcStub(nodeAccountId, false)
                    .updateTopic(transaction);
            case ConsensusDeleteTopic -> clients.getConsSvcStub(nodeAccountId, false)
                    .deleteTopic(transaction);
            case ConsensusSubmitMessage -> clients.getConsSvcStub(nodeAccountId, false)
                    .submitMessage(transaction);
            case UncheckedSubmit -> clients.getNetworkSvcStub(nodeAccountId, false)
                    .uncheckedSubmit(transaction);
            case TokenCreate -> clients.getTokenSvcStub(nodeAccountId, false).createToken(transaction);
            case TokenFreezeAccount -> clients.getTokenSvcStub(nodeAccountId, false)
                    .freezeTokenAccount(transaction);
            case TokenUnfreezeAccount -> clients.getTokenSvcStub(nodeAccountId, false)
                    .unfreezeTokenAccount(transaction);
            case TokenGrantKycToAccount -> clients.getTokenSvcStub(nodeAccountId, false)
                    .grantKycToTokenAccount(transaction);
            case TokenRevokeKycFromAccount -> clients.getTokenSvcStub(nodeAccountId, false)
                    .revokeKycFromTokenAccount(transaction);
            case TokenDelete -> clients.getTokenSvcStub(nodeAccountId, false).deleteToken(transaction);
            case TokenUpdate -> clients.getTokenSvcStub(nodeAccountId, false).updateToken(transaction);
            case TokenMint -> clients.getTokenSvcStub(nodeAccountId, false).mintToken(transaction);
            case TokenBurn -> clients.getTokenSvcStub(nodeAccountId, false).burnToken(transaction);
            case TokenAccountWipe -> clients.getTokenSvcStub(nodeAccountId, false)
                    .wipeTokenAccount(transaction);
            case TokenAssociateToAccount -> clients.getTokenSvcStub(nodeAccountId, false)
                    .associateTokens(transaction);
            case TokenReject -> clients.getTokenSvcStub(nodeAccountId, false).rejectToken(transaction);
            case TokenDissociateFromAccount -> clients.getTokenSvcStub(nodeAccountId, false)
                    .dissociateTokens(transaction);
            case TokenFeeScheduleUpdate -> clients.getTokenSvcStub(nodeAccountId, false)
                    .updateTokenFeeSchedule(transaction);
            case TokenPause -> clients.getTokenSvcStub(nodeAccountId, false).pauseToken(transaction);
            case TokenUnpause -> clients.getTokenSvcStub(nodeAccountId, false).unpauseToken(transaction);
            case ScheduleCreate -> clients.getScheduleSvcStub(nodeAccountId, false)
                    .createSchedule(transaction);
            case ScheduleDelete -> clients.getScheduleSvcStub(nodeAccountId, false)
                    .deleteSchedule(transaction);
            case ScheduleSign -> clients.getScheduleSvcStub(nodeAccountId, false)
                    .signSchedule(transaction);
            case UtilPrng -> clients.getUtilSvcStub(nodeAccountId, false).prng(transaction);
            case TokenUpdateNfts -> clients.getTokenSvcStub(nodeAccountId, false)
                    .updateNfts(transaction);
            case NodeDelete -> clients.getAddressBookSvcStub(nodeAccountId, false)
                    .deleteNode(transaction);
            case NodeCreate -> clients.getAddressBookSvcStub(nodeAccountId, false)
                    .createNode(transaction);
            case NodeUpdate -> clients.getAddressBookSvcStub(nodeAccountId, false)
                    .updateNode(transaction);
            case TokenAirdrop -> clients.getTokenSvcStub(nodeAccountId, false).airdropTokens(transaction);
            case TokenCancelAirdrop -> clients.getTokenSvcStub(nodeAccountId, false)
                    .cancelAirdrop(transaction);
            case TokenClaimAirdrop -> clients.getTokenSvcStub(nodeAccountId, false)
                    .claimAirdrop(transaction);
            default -> throw new IllegalArgumentException(functionality + " is not a transaction");
        };
    }
}
