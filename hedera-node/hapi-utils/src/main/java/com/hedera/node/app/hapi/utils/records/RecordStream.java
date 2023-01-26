package com.hedera.node.app.hapi.utils.records;

import com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.RecordStreamFile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

public class RecordStream {
    private final File streamDir;

    public RecordStream(final File streamDir) {
        this.streamDir = streamDir;
    }

    public Stream<RecordStreamFile> getRecordFileStream() {
        // Assume the streamUri is a directory
//        try {
////            return RecordStreamingUtils.orderedRecordFilesFrom(streamDir.toString()).stream();
//
//            throw new AssertionError("Not implemented");
//        } catch (IOException e) {
//            throw new UncheckedIOException(e);
//        }
        throw new AssertionError("Not implemented");
    }
}
