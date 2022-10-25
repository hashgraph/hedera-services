package com.hedera.node.app.service.file;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.jetbrains.annotations.NotNull;

public interface FileService extends Service {
    @NotNull
    @Override
    FilePreTransactionHandler createPreTransactionHandler(@NotNull States states);
}
