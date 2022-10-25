package com.hedera.node.app.service.contract;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

public interface ContractPreTransactionHandler extends PreTransactionHandler {
    /**
     * Creates a contract
     */
    TransactionMetadata preHandleCreateContract(TransactionBody txn);

    /**
     * Updates a contract with the content
     */
    TransactionMetadata preHandleUpdateContract(TransactionBody txn);

    /**
     * Calls a contract
     */
    TransactionMetadata preHandleContractCallMethod(TransactionBody txn);

    /**
     * Deletes a contract instance and transfers any remaining hbars to a specified receiver
     */
    TransactionMetadata preHandleDeleteContract(TransactionBody txn);

    /**
     * Deletes a contract if the submitting account has network admin privileges
     */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn);

    /**
     * Undeletes a contract if the submitting account has network admin privileges
     */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn);

    /**
     * Ethereum transaction
     */
    TransactionMetadata preHandleCallEthereum(TransactionBody txn);
}
