package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.standardized;

/**
 * Standardizes the failure status of a call to a HTS transfer system contract.
 */
public class CallStatusStandardizer {
    public static final CallStatusStandardizer CALL_STATUS_STANDARDIZER = new CallStatusStandardizer();

    private CallStatusStandardizer() {
        // Singleton
    }

    public ResponseCodeEnum codeForFailure(
            @NonNull final ResponseCodeEnum status,
            @NonNull final MessageFrame frame,
            @NonNull final CryptoTransferTransactionBody op) {
        if (status == INVALID_ACCOUNT_ID) {
            throw new AssertionError("Not implemented");
        } else {
            return standardized(status);
        }
    }
}
