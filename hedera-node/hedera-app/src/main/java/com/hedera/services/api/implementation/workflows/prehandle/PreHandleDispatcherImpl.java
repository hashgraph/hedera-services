package com.hedera.services.api.implementation.workflows.prehandle;

import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.services.api.implementation.ServicesAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.Objects;

public final class PreHandleDispatcherImpl implements PreHandleDispatcher {
    private final ServicesAccessor services;

    public PreHandleDispatcherImpl(ServicesAccessor services) {
        this.services = Objects.requireNonNull(services);
    }

    @Override
    public void dispatch(TransactionBody transactionBody) {
        switch (transactionBody.getDataCase()) {
//            case FILE_CREATE -> services.fileService().preHandler().preHandleFileCreate(transactionBodyData.as());
//            case CRYPTO_CREATE_ACCOUNT -> services.cryptoService().preHandler().preHandleAccountCreate(transactionBodyData.as());
            default ->
                    throw new IllegalArgumentException("Unexpected kind " + transactionBody.getDataCase());
        }
    }
}
