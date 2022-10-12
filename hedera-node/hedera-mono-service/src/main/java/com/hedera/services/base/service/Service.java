package com.hedera.services.base.service;

import com.hedera.services.base.state.States;

import javax.annotation.Nonnull;

public interface Service {
    @Nonnull PreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
