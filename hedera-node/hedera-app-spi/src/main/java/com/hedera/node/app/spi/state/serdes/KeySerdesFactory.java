package com.hedera.node.app.spi.state.serdes;

import com.hedera.node.app.spi.state.Serdes;

/**
 * A factory that creates a {@link Serdes} appropriate for a key type.
 *
 * <p>Mostly useful for packaging a PBJ {@code Writer} and {@code ProtoParser}.
 */
public class KeySerdesFactory {
    public static <T> Serdes<T> newInMemorySerdes(
            final SerdesParser<T> parser,
            final SerdesWriter<T> writer) {
        throw new AssertionError("Not implemented");
    }
}
