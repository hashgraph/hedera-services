/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.domain.security;

import static com.hedera.services.context.domain.security.PermissionFileUtils.permissionFileKeyForQuery;
import static com.hedera.services.context.domain.security.PermissionFileUtils.permissionFileKeyForTxn;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoQuery;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import org.junit.jupiter.api.Test;

class PermissionFileUtilsTest {
    @Test
    void returnsEmptyKeyForBlankTxn() {
        assertEquals("", permissionFileKeyForTxn(TransactionBody.getDefaultInstance()));
    }

    @Test
    void returnsEmptyKeyForBlankQuery() {
        assertEquals("", permissionFileKeyForQuery(Query.getDefaultInstance()));
    }

    @Test
    void worksForUtilPrng() {
        final var op = UtilPrngTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setUtilPrng(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForScheduleCreate() {
        final var op = ScheduleCreateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setScheduleCreate(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForScheduleDelete() {
        final var op = ScheduleDeleteTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setScheduleDelete(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForScheduleSign() {
        final var op = ScheduleSignTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setScheduleSign(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForCryptoCreateAccount() {
        final var op = CryptoCreateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setCryptoCreateAccount(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForCryptoTransfer() {
        final var op = CryptoTransferTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setCryptoTransfer(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForCryptoUpdateAccount() {
        final var op = CryptoUpdateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setCryptoUpdateAccount(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForCryptoDelete() {
        final var op = CryptoDeleteTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setCryptoDelete(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForCryptoAddLiveHash() {
        final var op = CryptoAddLiveHashTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setCryptoAddLiveHash(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForCryptoDeleteLiveHash() {
        final var op = CryptoDeleteLiveHashTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setCryptoDeleteLiveHash(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForFileCreate() {
        final var op = FileCreateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setFileCreate(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForFileUpdate() {
        final var op = FileUpdateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setFileUpdate(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForFileDelete() {
        final var op = FileDeleteTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setFileDelete(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForFileAppend() {
        final var op = FileAppendTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setFileAppend(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForContractCreateInstance() {
        final var op = ContractCreateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setContractCreateInstance(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForContractUpdateInstance() {
        final var op = ContractUpdateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setContractUpdateInstance(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForContractCall() {
        final var op = ContractCallTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setContractCall(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForTokenFeeScheduleUpdate() {
        final var op = TokenFeeScheduleUpdateTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setTokenFeeScheduleUpdate(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForSystemDelete() {
        final var op = SystemDeleteTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setSystemDelete(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForSystemUndelete() {
        final var op = SystemUndeleteTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setSystemUndelete(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForContractDeleteInstance() {
        final var op = ContractDeleteTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setContractDeleteInstance(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForFreeze() {
        final var op = FreezeTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setFreeze(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForConsensusCreateTopic() {
        final var op = ConsensusCreateTopicTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setConsensusCreateTopic(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForConsensusUpdateTopic() {
        final var op = ConsensusUpdateTopicTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setConsensusUpdateTopic(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForConsensusDeleteTopic() {
        final var op = ConsensusDeleteTopicTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setConsensusDeleteTopic(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForConsensusSubmitMessage() {
        final var op = ConsensusSubmitMessageTransactionBody.getDefaultInstance();
        final var txn = TransactionBody.newBuilder().setConsensusSubmitMessage(op).build();
        assertEquals(permissionFileKeyForTxn(txn), legacyKeyForTxn(txn));
    }

    @Test
    void worksForGetTopicInfo() {
        final var op = ConsensusGetTopicInfoQuery.getDefaultInstance();
        final var query = Query.newBuilder().setConsensusGetTopicInfo(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForNetworkGetExecutionTime() {
        final var op = NetworkGetExecutionTimeQuery.getDefaultInstance();
        final var query = Query.newBuilder().setNetworkGetExecutionTime(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetVersionInfo() {
        final var op = NetworkGetVersionInfoQuery.getDefaultInstance();
        final var query = Query.newBuilder().setNetworkGetVersionInfo(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetSolidityId() {
        final var op = GetBySolidityIDQuery.getDefaultInstance();
        final var query = Query.newBuilder().setGetBySolidityID(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetContractCallLocal() {
        final var op = ContractCallLocalQuery.getDefaultInstance();
        final var query = Query.newBuilder().setContractCallLocal(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetContractInfo() {
        final var op = ContractGetInfoQuery.getDefaultInstance();
        final var query = Query.newBuilder().setContractGetInfo(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetContractBytecode() {
        final var op = ContractGetBytecodeQuery.getDefaultInstance();
        final var query = Query.newBuilder().setContractGetBytecode(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetContractRecords() {
        final var op = ContractGetRecordsQuery.getDefaultInstance();
        final var query = Query.newBuilder().setContractGetRecords(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetCryptoBalance() {
        final var op = CryptoGetAccountBalanceQuery.getDefaultInstance();
        final var query = Query.newBuilder().setCryptogetAccountBalance(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetCryptoRecords() {
        final var op = CryptoGetAccountRecordsQuery.getDefaultInstance();
        final var query = Query.newBuilder().setCryptoGetAccountRecords(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetCryptoInfo() {
        final var op = CryptoGetInfoQuery.getDefaultInstance();
        final var query = Query.newBuilder().setCryptoGetInfo(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetLiveHash() {
        final var op = CryptoGetLiveHashQuery.getDefaultInstance();
        final var query = Query.newBuilder().setCryptoGetLiveHash(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetFileContents() {
        final var op = FileGetContentsQuery.getDefaultInstance();
        final var query = Query.newBuilder().setFileGetContents(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForGetFileinfo() {
        final var op = FileGetInfoQuery.getDefaultInstance();
        final var query = Query.newBuilder().setFileGetInfo(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForReceipt() {
        final var op = TransactionGetReceiptQuery.getDefaultInstance();
        final var query = Query.newBuilder().setTransactionGetReceipt(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForRecord() {
        final var op = TransactionGetRecordQuery.getDefaultInstance();
        final var query = Query.newBuilder().setTransactionGetRecord(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    @Test
    void worksForFastRecord() {
        final var op = TransactionGetFastRecordQuery.getDefaultInstance();
        final var query = Query.newBuilder().setTransactionGetFastRecord(op).build();
        assertEquals(legacyKeyForQuery(query), permissionFileKeyForQuery(query));
    }

    private String legacyKeyForQuery(Query request) {
        String queryBody = null;
        switch (request.getQueryCase()) {
            case NETWORKGETEXECUTIONTIME:
                queryBody = "networkGetExecutionTime";
                break;
            case NETWORKGETVERSIONINFO:
                queryBody = "getVersionInfo";
                break;
            case GETBYKEY:
                break;
            case CONSENSUSGETTOPICINFO:
                queryBody = "getTopicInfo";
                break;
            case GETBYSOLIDITYID:
                queryBody = "getBySolidityID";
                break;
            case CONTRACTCALLLOCAL:
                queryBody = "contractCallLocalMethod";
                break;
            case CONTRACTGETINFO:
                queryBody = "getContractInfo";
                break;
            case SCHEDULEGETINFO:
                queryBody = "getScheduleInfo";
                break;
            case CONTRACTGETBYTECODE:
                queryBody = "contractGetBytecode";
                break;
            case CONTRACTGETRECORDS:
                queryBody = "getTxRecordByContractID";
                break;
            case CRYPTOGETACCOUNTBALANCE:
                queryBody = "cryptoGetBalance";
                break;
            case CRYPTOGETACCOUNTRECORDS:
                queryBody = "getAccountRecords";
                break;
            case CRYPTOGETINFO:
                queryBody = "getAccountInfo";
                break;
            case CRYPTOGETLIVEHASH:
                queryBody = "getLiveHash";
                break;
            case CRYPTOGETPROXYSTAKERS:
                break;
            case FILEGETCONTENTS:
                queryBody = "getFileContent";
                break;
            case FILEGETINFO:
                queryBody = "getFileInfo";
                break;
            case TRANSACTIONGETRECEIPT:
                queryBody = "getTransactionReceipts";
                break;
            case TRANSACTIONGETRECORD:
                queryBody = "getTxRecordByTxID";
                break;
            case TRANSACTIONGETFASTRECORD:
                queryBody = "getFastTransactionRecord";
                break;
            case QUERY_NOT_SET:
                break;
            default:
                queryBody = null;
        }
        return queryBody;
    }

    private String legacyKeyForTxn(TransactionBody txn) {
        String key = "";
        if (txn.hasCryptoCreateAccount()) {
            key = "createAccount";
        } else if (txn.hasCryptoTransfer()) {
            key = "cryptoTransfer";
        } else if (txn.hasCryptoUpdateAccount()) {
            key = "updateAccount";
        } else if (txn.hasCryptoDelete()) {
            key = "cryptoDelete";
        } else if (txn.hasCryptoAddLiveHash()) {
            key = "addLiveHash";
        } else if (txn.hasCryptoDeleteLiveHash()) {
            key = "deleteLiveHash";
        } else if (txn.hasFileCreate()) {
            key = "createFile";
        } else if (txn.hasFileUpdate()) {
            key = "updateFile";
        } else if (txn.hasFileDelete()) {
            key = "deleteFile";
        } else if (txn.hasFileAppend()) {
            key = "appendContent";
        } else if (txn.hasContractCreateInstance()) {
            key = "createContract";
        } else if (txn.hasContractUpdateInstance()) {
            key = "updateContract";
        } else if (txn.hasContractCall()) {
            key = "contractCallMethod";
        } else if (txn.hasSystemDelete()) {
            key = "systemDelete";
        } else if (txn.hasSystemUndelete()) {
            key = "systemUndelete";
        } else if (txn.hasContractDeleteInstance()) {
            key = "deleteContract";
        } else if (txn.hasFreeze()) {
            key = "freeze";
        } else if (txn.hasConsensusCreateTopic()) {
            key = "createTopic";
        } else if (txn.hasConsensusUpdateTopic()) {
            key = "updateTopic";
        } else if (txn.hasConsensusDeleteTopic()) {
            key = "deleteTopic";
        } else if (txn.hasScheduleCreate()) {
            key = "scheduleCreate";
        } else if (txn.hasScheduleDelete()) {
            key = "scheduleDelete";
        } else if (txn.hasScheduleSign()) {
            key = "scheduleSign";
        } else if (txn.hasConsensusSubmitMessage()) {
            key = "submitMessage";
        } else if (txn.hasTokenFeeScheduleUpdate()) {
            key = "tokenFeeScheduleUpdate";
        } else if (txn.hasUtilPrng()) {
            key = "utilPrng";
        }
        return key;
    }
}
