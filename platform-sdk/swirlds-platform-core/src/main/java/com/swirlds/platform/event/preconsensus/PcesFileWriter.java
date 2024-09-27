package com.swirlds.platform.event.preconsensus;

import com.hedera.hapi.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public interface PcesFileWriter {

    void writeVersion(int version) throws IOException;

    void writeEvent(@NonNull final GossipEvent event) throws IOException;

    /**
     * Flush the file.
     */
    void flush() throws IOException;

    /**
     * Close the file.
     */
    void close() throws IOException;

    long fileSize();
}
