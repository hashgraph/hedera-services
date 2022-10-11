package com.hedera.services.state.virtual.utils;

import java.io.IOException;

public interface CheckedSupplier<T> {
    T get() throws IOException;
}
