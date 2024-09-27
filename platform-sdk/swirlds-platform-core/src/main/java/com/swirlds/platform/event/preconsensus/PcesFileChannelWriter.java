package com.swirlds.platform.event.preconsensus;

import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class PcesFileChannelWriter implements PcesFileWriter {
    final FileChannel channel;
    final ByteBuffer buffer;
    final WritableSequentialData writableSequentialData;

    public PcesFileChannelWriter(@NonNull final Path filePath) throws IOException {
        channel = FileChannel.open(filePath);
        buffer = ByteBuffer.allocateDirect(1024*1024*10);
        writableSequentialData = BufferedData.wrap(buffer);

        buffer.putInt(1);
        final int bytesWritten = channel.write(buffer);
        if (bytesWritten != Integer.BYTES) {
            throw new IOException("Failed to write version number to file");
        }
    }

    @Override
    public void writeEvent(@NonNull final GossipEvent event) throws IOException {
        buffer.clear();
        buffer.putInt(GossipEvent.PROTOBUF.measureRecord(event));
        GossipEvent.PROTOBUF.write(event, writableSequentialData);
        buffer.flip();
        final int bytesWritten = channel.write(buffer);
        if (bytesWritten != buffer.limit()) {
            throw new IOException("Failed to write event to file");
        }
    }

    @Override
    public void flush() throws IOException {
        channel.force(false);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
