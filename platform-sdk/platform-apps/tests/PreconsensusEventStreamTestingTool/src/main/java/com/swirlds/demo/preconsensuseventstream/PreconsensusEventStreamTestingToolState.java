package com.swirlds.demo.preconsensuseventstream;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the Preconsensus Event Stream Testing Tool
 */
public class PreconsensusEventStreamTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {
    private static final Logger logger = LogManager.getLogger(PreconsensusEventStreamTestingToolState.class);
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
    public PreconsensusEventStreamTestingToolState() {
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor
     */
    private PreconsensusEventStreamTestingToolState(@NonNull final PreconsensusEventStreamTestingToolState that) {
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
    public void handleConsensusRound(final @NonNull Round round, final @NonNull SwirldDualState swirldDualState) {
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized PreconsensusEventStreamTestingToolState copy() {
        throwIfImmutable();
        return new PreconsensusEventStreamTestingToolState(this);
    }
}
