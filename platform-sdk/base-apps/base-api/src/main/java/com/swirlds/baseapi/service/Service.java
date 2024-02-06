package com.swirlds.baseapi.service;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;

public class Service<T> {

    private final Class<T> resultType;

    public Service(@NonNull final Class<T> resultType) {
        this.resultType = resultType;
    }

    public void delete(final @NonNull String key) {
        throw new UnsupportedOperationException("Delete Operation not supported");
    }

    public @NonNull T update(final @NonNull String key, final @NonNull T body) {
        throw new UnsupportedOperationException("Update Operation not supported");
    }

    public @NonNull T create(final @NonNull T body) {
        throw new UnsupportedOperationException("Create Operation not supported");
    }

    public @Nullable T retrieve(final @NonNull String key) {
        throw new UnsupportedOperationException("Retrieve Operation not supported");
    }

    public @NonNull List<T> retrieveAll(@NonNull Map<String, String> params) {
        throw new UnsupportedOperationException("Retrieve All Operation not supported");
    }

    public @NonNull Class<T> getResultType() {
        return this.resultType;
    }
}
