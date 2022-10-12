package com.hedera.services.base.service;

import com.hedera.services.base.store.AccountStore;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.utils.EntityNum;

/**
 * An implementation of {@code CryptoPreTransactionHandler} for validating all transactions defined in the protobuf
 * "CryptoService" in pre-handle. It adds all the verified signatures to the  including signature verification.
 */
public class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final AccountStore accountStore;

    public CryptoPreTransactionHandlerImpl(final AccountStore accountStore){
        this.accountStore = accountStore;
    }

    public AccountSigningMetadata getAccountSigningMetadata(EntityNum accountNum){
        final var key = accountStore.getAccountKey(accountNum);
        final var receiverSigRequired = accountStore.isReceiverSigRequired(accountNum);
        return new AccountSigningMetadata(key, receiverSigRequired);
    }
}
