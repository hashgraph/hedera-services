package com.hedera.node.app.fixtures.workflows;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fixtures.Scenarios;

public interface BadTransactionScenarios extends Scenarios {

    default TransactionScenarioBuilder scenario() {
        return new TransactionScenarioBuilder();
    }

    default Transaction missingAllFields() {
        return Transaction.DEFAULT;
    }

//    default Transaction missingTransactionID() {
//        return with(TransactionBody.DEFAULT)
//                .withTransactionID(null)
//                .build();
//    }
//
//    default Transaction missingTransactionFee() {
//        return with(TransactionBody.DEFAULT)
//                .withTransactionFee(0)
//                .build();
//    }
}
