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
package com.hedera.services.legacy.handler;

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_SYSTEM_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.builder.RequestBuilder.getTimestamp;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionReceipt;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionRecord;

import com.google.protobuf.TextFormat;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Post-consensus execution of smart contract api calls */
@Singleton
public class SmartContractRequestHandler {
    private static final Logger log = LogManager.getLogger(SmartContractRequestHandler.class);

    private final Map<EntityId, Long> entityExpiries;

    private final HederaLedger ledger;
    private final HbarCentExchange exchange;

    @Inject
    public SmartContractRequestHandler(
            final HederaLedger ledger,
            final HbarCentExchange exchange,
            final Map<EntityId, Long> entityExpiries) {
        this.ledger = ledger;
        this.exchange = exchange;
        this.entityExpiries = entityExpiries;
    }

    /**
     * System account deletes any contract. This simply marks the contract as deleted.
     *
     * @param txBody API request to delete the contract
     * @param consensusTimestamp Platform consensus time
     * @return Details of contract deletion result
     */
    public TransactionRecord systemDelete(TransactionBody txBody, Instant consensusTimestamp) {
        SystemDeleteTransactionBody op = txBody.getSystemDelete();
        ContractID cid = op.getContractID();
        long newExpiry = op.getExpirationTime().getSeconds();
        TransactionReceipt receipt;
        receipt = updateDeleteFlag(cid, true);
        try {
            if (receipt.getStatus().equals(ResponseCodeEnum.SUCCESS)) {
                AccountID id = asAccount(cid);
                long oldExpiry = ledger.expiry(id);
                var entity = EntityId.fromGrpcContractId(cid);
                entityExpiries.put(entity, oldExpiry);
                HederaAccountCustomizer customizer =
                        new HederaAccountCustomizer().expiry(newExpiry);
                ledger.customizePotentiallyDeleted(id, customizer);
            }
        } catch (Exception e) {
            log.warn("Unhandled exception in SystemDelete", e);
            log.debug(
                    "File System Exception {} tx= {}",
                    () -> e,
                    () -> TextFormat.shortDebugString(op));
            receipt =
                    getTransactionReceipt(
                            ResponseCodeEnum.FILE_SYSTEM_EXCEPTION, exchange.activeRates());
        }

        TransactionRecord.Builder transactionRecord =
                getTransactionRecord(
                        txBody.getTransactionFee(),
                        txBody.getMemo(),
                        txBody.getTransactionID(),
                        getTimestamp(consensusTimestamp),
                        receipt);
        return transactionRecord.build();
    }

    /**
     * System account undoes the deletion marker on a smart contract that has been deleted but not
     * yet removed.
     *
     * @param txBody API reuest to undelete the contract
     * @param consensusTimestamp Platform consensus time
     * @return Details of contract undeletion result
     */
    public TransactionRecord systemUndelete(TransactionBody txBody, Instant consensusTimestamp) {
        SystemUndeleteTransactionBody op = txBody.getSystemUndelete();
        ContractID cid = op.getContractID();
        var entity = EntityId.fromGrpcContractId(cid);
        TransactionReceipt receipt = getTransactionReceipt(SUCCESS, exchange.activeRates());

        long oldExpiry = 0;
        try {
            if (entityExpiries.containsKey(entity)) {
                oldExpiry = entityExpiries.get(entity);
            } else {
                receipt = getTransactionReceipt(INVALID_FILE_ID, exchange.activeRates());
            }
            if (oldExpiry > 0) {
                HederaAccountCustomizer customizer =
                        new HederaAccountCustomizer().expiry(oldExpiry);
                ledger.customizePotentiallyDeleted(asAccount(cid), customizer);
            }
            if (receipt.getStatus() == SUCCESS) {
                try {
                    receipt = updateDeleteFlag(cid, false);
                } catch (Exception e) {
                    receipt = getTransactionReceipt(FAIL_INVALID, exchange.activeRates());
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "systemUndelete exception: can't serialize or deserialize! tx= {}"
                                        + " {}",
                                txBody,
                                e);
                    }
                }
            }
            entityExpiries.remove(entity);
        } catch (Exception e) {
            log.warn("Unhandled exception in SystemUndelete", e);
            log.debug(
                    "File System Exception {} tx= {}",
                    () -> e,
                    () -> TextFormat.shortDebugString(op));
            receipt = getTransactionReceipt(FILE_SYSTEM_EXCEPTION, exchange.activeRates());
        }
        TransactionRecord.Builder transactionRecord =
                getTransactionRecord(
                        txBody.getTransactionFee(),
                        txBody.getMemo(),
                        txBody.getTransactionID(),
                        getTimestamp(consensusTimestamp),
                        receipt);
        return transactionRecord.build();
    }

    private TransactionReceipt updateDeleteFlag(ContractID cid, boolean deleted) {
        var id = asAccount(cid);
        if (ledger.isDeleted(id)) {
            ledger.customizePotentiallyDeleted(
                    asAccount(cid), new HederaAccountCustomizer().isDeleted(deleted));
        } else {
            ledger.customize(asAccount(cid), new HederaAccountCustomizer().isDeleted(deleted));
        }
        return getTransactionReceipt(SUCCESS, exchange.activeRates());
    }
}
