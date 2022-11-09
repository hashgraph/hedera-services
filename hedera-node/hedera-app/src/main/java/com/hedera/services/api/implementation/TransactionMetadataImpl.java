package com.hedera.services.api.implementation;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.List;

public class TransactionMetadataImpl implements TransactionMetadata {

    private final TransactionBody transactionBody;
    private final ResponseCodeEnum status;

    public TransactionMetadataImpl(TransactionBody transactionBody) {
        this(transactionBody, ResponseCodeEnum.OK);
    }

    protected TransactionMetadataImpl(TransactionBody transactionBody, ResponseCodeEnum status) {
        this.transactionBody = transactionBody;
        this.status = status;
    }

    @Override
    public ResponseCodeEnum status() {
        return status;
    }

    @Override
    public TransactionBody getTxn() {
        return transactionBody;
    }

    @Override
    public List<HederaKey> getReqKeys() {
        return List.of();
    }

}
