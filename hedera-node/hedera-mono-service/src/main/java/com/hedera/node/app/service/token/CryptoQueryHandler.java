package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.QueryHandler;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.*;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Optional;

/**
 * Defines APIs for responding to queries on state. Most of these APIs are defined in "CryptoService"
 * in the protobuf. Some APIs are defined for the use of other modules, or the Hedera application.
 * Some queries are paid, some, some are free.
 */
public interface CryptoQueryHandler extends QueryHandler {
    /**
     * Retrieves an {@link Account} given an {@link AccountID}. This method is not defined in
     * "CryptoService", but exists for the use of other modules, including the Hedera application.
     *
     * @param id The id. Cannot be null.
     * @return A non-null {@link Optional} with a reference to the {@link Account}, or empty if there is not one
     *         matching the given ID.
     */
    Optional<Account> getAccountById(@NonNull AccountID id);

    /**
     * Returns all transactions in the last 180s of consensus time for which the given account was
     * the effective payer <b>and</b> network property <tt>ledger.keepRecordsInState</tt> was
     * <tt>true</tt>.
     */
    void getAccountRecords(@NonNull CryptoGetAccountRecordsQuery query);

    /**
     * Retrieves the balance of an account
     */
    void cryptoGetBalance(@NonNull CryptoGetAccountBalanceQuery query);

    /**
     * Retrieves the metadata of an account
     */
    void getAccountInfo(@NonNull GetAccountDetailsQuery query);

    /**
     * Retrieves the latest receipt for a transaction that is either awaiting consensus, or reached
     * consensus in the last 180 seconds
     */
    void getTransactionReceipts(@NonNull TransactionGetReceiptQuery query);

    /**
     * Retrieves the record of a transaction that is either awaiting consensus, or reached consensus
     * in the last 180 seconds
     */
    void getTxRecordByTxID(@NonNull TransactionGetRecordQuery query);
}
