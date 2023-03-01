package com.hedera.node.app.spi.state.serdes;

import edu.umd.cs.findbugs.annotations.NonNull;

import com.hedera.hashgraph.pbj.runtime.io.DataOutput;
import java.io.IOException;

@FunctionalInterface
public interface PbjWriter<T> {
    void write(@NonNull T item, @NonNull DataOutput output) throws IOException;
}
