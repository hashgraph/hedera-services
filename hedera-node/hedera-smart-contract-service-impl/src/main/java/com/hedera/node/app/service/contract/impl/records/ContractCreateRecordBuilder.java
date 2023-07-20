package com.hedera.node.app.service.contract.impl.records;

import com.hedera.hapi.node.base.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface ContractCreateRecordBuilder {
    /**
     * Tracks the result of a top-level contract creation.
     *
     * @param contractId the {@link ContractID} of the new top-level contract
     * @return this builder
     */
    @NonNull
    ContractCreateRecordBuilder createdId(@NonNull ContractID contractId);
}
