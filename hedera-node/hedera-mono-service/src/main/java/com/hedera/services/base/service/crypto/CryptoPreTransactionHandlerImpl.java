package com.hedera.services.base.service.crypto;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.base.metadata.TransactionMetadata;
import com.hedera.services.base.store.AccountStore;
import com.hederahashgraph.api.proto.java.Transaction;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;

/**
 * An implementation of {@code CryptoPreTransactionHandler} for validating all transactions defined in the protobuf
 * "CryptoService" in pre-handle. It adds all the verified signatures to the  including signature verification.
 */
public class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final AccountStore accountStore;

    public CryptoPreTransactionHandlerImpl(final AccountStore accountStore){
        this.accountStore = accountStore;
    }

    public TransactionMetadata cryptoCreate(final Transaction tx) {
        try{
            final var txnBody = extractTransactionBody(tx);
            final var payer = txnBody.getTransactionID().getAccountID();
            return accountStore.createAccountSigningMetadata(tx, payer);
        } catch (InvalidProtocolBufferException ex){
            return new TransactionMetadata(tx, true, null, null);
        }
    }
}
