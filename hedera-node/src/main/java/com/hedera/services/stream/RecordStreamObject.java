package com.hedera.services.stream;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializableRunningHashable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.stream.Timestamped;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;
import java.time.Instant;

/**
 * Contains a TransactionRecord, its related Transaction, and consensus Timestamp of the Transaction.
 * Is used for record streaming
 */
public class RecordStreamObject extends AbstractSerializableHashable implements Timestamped,
		SerializableRunningHashable {
	public static final long CLASS_ID = 0xe370929ba5429d8bL;
	public static final int CLASS_VERSION = 1;

	//TODO: confirm the max length;
	private static final int MAX_RECORD_LENGTH = 64 * 1024;
	private static final int MAX_TRANSACTION_LENGTH = 64 * 1024;

	/** the {@link TransactionRecord} object to be written to record stream file */
	private TransactionRecord transactionRecord;

	/** the {@link Transaction} object to be written to record stream file */
	private Transaction transaction;

	/**
	 * the consensus timestamp of this {@link TransactionRecord} object,
	 * this field is used for deciding wether to start a new record stream file,
	 * and for generating file name when starting to write a new record stream file;
	 * this field is not written to record stream file
	 */
	private Instant consensusTimestamp;

	/**
	 * this RunningHash instance encapsulates a Hash object which denotes a running Hash calculated from
	 * all RecordStreamObject in history up to this RecordStreamObject instance
	 */
	private RunningHash runningHash;

	public RecordStreamObject() {
	}

	public RecordStreamObject(final TransactionRecord transactionRecord,
			final Transaction transaction, final Instant consensusTimestamp) {
		this.transactionRecord = transactionRecord;
		this.transaction = transaction;
		this.consensusTimestamp = consensusTimestamp;
		runningHash = new RunningHash();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeByteArray(transactionRecord.toByteArray());
		out.writeByteArray(transaction.toByteArray());
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		transactionRecord = TransactionRecord.parseFrom(in.readByteArray(MAX_RECORD_LENGTH));
		transaction = Transaction.parseFrom(in.readByteArray(MAX_TRANSACTION_LENGTH));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	@Override
	public Instant getTimestamp() {
		return consensusTimestamp;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("TransactionRecord", transactionRecord)
				.append("Transaction", transaction)
				.append("ConsensusTimestamp", consensusTimestamp).toString();
	}

	/**
	 * only show TransactionID in the record and consensusTimestamp
	 *
	 * @return
	 */
	public String toShortString() {
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("TransactionRecord", toShortStringRecord(transactionRecord))
				.append("ConsensusTimestamp", consensusTimestamp).toString();
	}

	public static String toShortStringRecord(TransactionRecord transactionRecord) {
		return new ToStringBuilder(transactionRecord, ToStringStyle.NO_CLASS_NAME_STYLE)
				.append("TransactionID", transactionRecord.getTransactionID()).toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		RecordStreamObject that = (RecordStreamObject) obj;
		return new EqualsBuilder().
				append(this.transactionRecord, that.transactionRecord).
				append(this.transaction, that.transaction).
				append(this.consensusTimestamp, that.consensusTimestamp).
				isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().
				append(transactionRecord).
				append(transaction).
				append(consensusTimestamp).
				toHashCode();
	}

	@Override
	public RunningHash getRunningHash() {
		return runningHash;
	}
}
