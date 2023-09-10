package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface AddressIdConverter {
    /**
     * Given an address to be referenced in a synthetic {@link com.hedera.hapi.node.transaction.TransactionBody},
     * returns the {@link AccountID} that should be used in the synthetic transaction.
     *
     * @param address the address to be used in the synthetic transaction
     * @return the {@link AccountID} that should be used in the synthetic transaction
     */
    @NonNull
    AccountID convert(@NonNull Address address);

    /**
     * Given an address to be credited in a synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody},
     * returns the {@link AccountID} that should be used in the synthetic transaction.
     *
     * <p>Follows the logic used in mono-service, despite it being slightly odd in the case of non-canonical
     * references (i.e., when an account with a EVM address is referenced by its long-zero address).
     *
     * @param address the address to be used in the synthetic transaction
     * @return the {@link AccountID} that should be used in the synthetic transaction
     */
    @NonNull
    AccountID convertCredit(@NonNull final Address address);
}
