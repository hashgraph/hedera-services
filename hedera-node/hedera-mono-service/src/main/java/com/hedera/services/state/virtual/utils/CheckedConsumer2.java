package com.hedera.services.state.virtual.utils;

import java.io.IOException;

public interface CheckedConsumer2<T, U> {
    void accept(T t, U u) throws IOException;
}
