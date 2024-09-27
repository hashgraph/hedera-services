package com.swirlds.platform.event.preconsensus;

import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class PcesLegacyFileWriter implements PcesFileWriter{
    /**
     * The output stream to write to.
     */
    private final SerializableDataOutputStream out;
    /**
     * Counts the bytes written to the file.
     */
    private final CountingStreamExtension counter;

    public PcesLegacyFileWriter(@NonNull final Path filePath) throws FileNotFoundException {
        counter = new CountingStreamExtension(false);
        out = new SerializableDataOutputStream(new ExtendableOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(filePath.toFile())),
                counter));
    }

    @Override
    public void writeVersion(final int version) throws IOException {
        out.writeInt(version);
    }

    @Override
    public void writeEvent(@NonNull final GossipEvent event) throws IOException {
        out.writePbjRecord(event, GossipEvent.PROTOBUF);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public long fileSize() {
        return counter.getCount();
    }
}
