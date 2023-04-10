package com.swirlds.demo.consistency;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.NonCryptographicHashing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the Consistency Testing Tool
 */
public class ConsistencyTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {
    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolState.class);
    private static final long CLASS_ID = 0xda03bb07eb897d82L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The true "state" of this app. This long value is updated with every transaction
     */
    private long stateLong = 0;

    /**
     * Constructor
     */
    public ConsistencyTestingToolState() {
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor
     */
    private ConsistencyTestingToolState(@NonNull final ConsistencyTestingToolState that) {
        super(throwArgNull(that, "that"));
        this.stateLong = that.stateLong;
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
    public void serialize(final @NonNull SerializableDataOutputStream out) throws IOException {
        throwArgNull(out, "the serializable data output stream cannot be null");
        out.writeLong(stateLong);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final @NonNull SerializableDataInputStream in, final int version) throws IOException {
        throwArgNull(in, "the serializable data input stream cannot be null");
        stateLong = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ConsistencyTestingToolState copy() {
        throwIfImmutable();
        return new ConsistencyTestingToolState(this);
    }

    /**
     * Sets the new {@link #stateLong} to the non-cryptographic hash of the existing state, and the contents of the
     * transaction being handled
     *
     * @param transaction the transaction to apply to the state
     */
    private void applyTransactionToState(final @NonNull ConsensusTransaction transaction) {
        throwArgNull(transaction, "transaction");

        final long transactionContents = byteArrayToLong(transaction.getContents(), 0);
        stateLong = NonCryptographicHashing.hash64(stateLong, transactionContents);
    }

    /**
     * Modifies the state based on each transaction in the round
     * <p>
     * Writes the round and its contents to a log on disk
     */
    @Override
    public void handleConsensusRound(final @NonNull Round round, final @NonNull SwirldDualState swirldDualState) {
        round.forEachTransaction(this::applyTransactionToState);
        StateLogWriter.writeRoundStateToLog(round);
    }
}
