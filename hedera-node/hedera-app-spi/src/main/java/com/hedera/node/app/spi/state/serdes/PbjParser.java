package com.hedera.node.app.spi.state.serdes;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import com.hedera.hashgraph.pbj.runtime.io.DataInput;

@FunctionalInterface
public interface PbjParser<T> {
    @NonNull
    T parse(@NonNull DataInput input) throws IOException;
}
