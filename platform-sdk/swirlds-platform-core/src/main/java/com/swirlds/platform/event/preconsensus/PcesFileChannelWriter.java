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
    private int fileSize;

    public PcesFileChannelWriter(@NonNull final Path filePath) throws IOException {
        channel = FileChannel.open(filePath);
        buffer = ByteBuffer.allocateDirect(1024*1024*10);
        writableSequentialData = BufferedData.wrap(buffer);
    }

    @Override
    public void writeVersion(final int version) throws IOException {
        buffer.clear();
        buffer.putInt(version);
        flipAndWrite();
    }

    @Override
    public void writeEvent(@NonNull final GossipEvent event) throws IOException {
        buffer.clear();
        buffer.putInt(GossipEvent.PROTOBUF.measureRecord(event));
        GossipEvent.PROTOBUF.write(event, writableSequentialData);
        flipAndWrite();
    }

    private void flipAndWrite() throws IOException {
        buffer.flip();
        final int bytesWritten = channel.write(buffer);
        fileSize += bytesWritten;
        if (bytesWritten != buffer.limit()) {
            throw new IOException("Failed to write data to file. Wrote " + bytesWritten + " bytes out of " + buffer.limit());
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

    @Override
    public long fileSize() {
        return fileSize;
    }
}
