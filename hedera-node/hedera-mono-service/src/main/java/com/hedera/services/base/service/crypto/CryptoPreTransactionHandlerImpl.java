package com.hedera.services.base.service.crypto;

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

    public AccountSigningMetadata getAccountSigningMetadata(final EntityNum accountNum){
        final var account = accountStore.getAccount(accountNum);
        if (account.isPresent()) {
            final var receiverSigRequired = account.get().isReceiverSigRequired();
            final var key = account.get().key().get();
            return new AccountSigningMetadata(key, receiverSigRequired);
        }
        throw new IllegalArgumentException("Provided account number doesn't exist");
    }
}
