package com.hedera.node.app.spi.state.serdes;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.DataInput;
import java.io.IOException;

@FunctionalInterface
public interface SerdesParser<T> {
    @NonNull
    T parse(@NonNull DataInput input) throws IOException;
}
