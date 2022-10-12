package com.hedera.services.base.service;

import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.utils.EntityNum;

/**
 * A definition of an interface for validating all transactions defined in the protobuf "CryptoService"
 * in pre-handle including signature verification.
 */
public interface CryptoPreTransactionHandler extends PreTransactionHandler {
    AccountSigningMetadata getAccountSigningMetadata(EntityNum accountNum);
}
