package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This wrapper is used to better dispatch the decoding result from the decoder to the translator.
 * Its purpose is to help us replicate the mono behavior by either reverting the transaction or returning certain status.
 * - body: Represents the transaction body.
 * - shouldRevert: Indicates if the transaction should be reverted.
 * - status: Provides the specific response status code for the transaction, if needed.
 */

public record DecoderResult(@NonNull TransactionBody body, @Nullable ResponseCodeEnum status, boolean shouldRevert) {
}
