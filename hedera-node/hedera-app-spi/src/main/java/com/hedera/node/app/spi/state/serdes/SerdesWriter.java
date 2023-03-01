package com.hedera.node.app.spi.state.serdes;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.DataOutput;
import java.io.IOException;

@FunctionalInterface
public interface SerdesWriter<T> {
    void write(@NonNull T item, @NonNull DataOutput output) throws IOException;

}
