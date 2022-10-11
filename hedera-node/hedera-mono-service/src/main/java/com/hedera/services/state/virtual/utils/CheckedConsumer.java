package com.hedera.services.state.virtual.utils;

import java.io.IOException;

public interface CheckedConsumer<T> {
    void accept(T t) throws IOException;
}
