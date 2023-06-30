package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;

import java.util.List;

/**
 * Charges custom fees for the crypto transfer operation. This is yet to be implemented
 */
public class CustomFeeAssessmentStep {
    public CustomFeeAssessmentStep(final CryptoTransferTransactionBody op) {
    }

    public List<TransactionBody.Builder> assessCustomFees(final CryptoTransferTransactionBody op) {
       return null;
    }
}
