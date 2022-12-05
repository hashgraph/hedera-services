package com.hedera.node.app.spi;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

/**
 * Returns needed pre-transaction handlers for different modules.
 */
public interface CallContext {
    /**
     * Gets pre-transaction handler for CryptoService
     * @return pre-transaction handler for CryptoService
     */
    PreTransactionHandler getCryptoPreTransactionHandler();

    /**
     * Gets pre-transaction handler for ScheduleService
     * @return pre-transaction handler for ScheduleService
     */
    PreTransactionHandler getSchedulePreTransactionHandler();

    /**
     * Gets the pre-transaction handler based on the type of transaction
     * @param function type of transaction
     * @return pre-transaction handler based on the type of transaction
     */
    default PreTransactionHandler getPreTxnHandler(final HederaFunctionality function){
        if(function == CryptoTransfer || function == CryptoCreate || function == CryptoDelete ||
                function == CryptoDeleteAllowance || function == CryptoApproveAllowance){
            return getCryptoPreTransactionHandler();
        }else if(function == ScheduleCreate || function == ScheduleDelete || function == ScheduleSign){
            return getSchedulePreTransactionHandler();
        }
        throw new UnsupportedOperationException("Pre-transaction handler for given function " + function + " is not supported yet !");
    }
}
