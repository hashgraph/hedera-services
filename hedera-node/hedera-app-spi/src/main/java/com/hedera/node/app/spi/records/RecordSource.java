package com.hedera.node.app.spi.records;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;

/**
 * A source of queryable {@link TransactionReceipt} and {@link TransactionRecord} records for one or more
 * {@link TransactionID}'s.
 */
public interface RecordSource {
    /**
     * This receipt is returned whenever we know there is a transaction pending (i.e. we have a history for a
     * transaction ID), but we do not yet have a record for it.
     */
    TransactionReceipt PENDING_RECEIPT =
            TransactionReceipt.newBuilder().status(UNKNOWN).build();

    /**
     * All the transaction ids for which this source has (or will have) records.
     * @return the transaction ids
     */
    List<TransactionID> txnIds();

    /**
     * Gets the user transaction record for the given transaction id, if known.
     * @return the record, or {@code null} if the id is unknown
     */
    @Nullable
    TransactionRecord userTransactionRecord(@NonNull TransactionID txnId);

    /**
     * Gets the primary receipt, that is, the receipt associated with the user transaction for the given
     * transaction id, if known.
     * @return the receipt, or {@code null} if the id is unknown
     */
    @Nullable
    TransactionReceipt userTransactionReceipt(@NonNull TransactionID txnId);

    /**
     * Gets the list of all duplicate records for the given transaction id, as a view. Should the list of records change, this view will reflect
     * those changes.
     *
     * @return the list of all duplicate records
     */
    @NonNull
    List<TransactionRecord> duplicateRecords(@NonNull TransactionID txnId);

    /**
     * Gets the number of duplicate records for the given transaction id.
     *
     * @return the number of duplicate records
     */
    int duplicateCount(@NonNull TransactionID txnId);

    /**
     * Returns a list of all records for the given transaction id, ordered by consensus timestamp.
     *
     * @return the list of all records, ordered by consensus timestamp
     */
    List<TransactionRecord> orderedRecords(@NonNull TransactionID txnId);
}
