package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.annotations.QueryScope;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A read-only {@link ExtWorldScope} implementation based on a {@link QueryContext}.
 */
@QueryScope
public class QueryExtWorldScope implements ExtWorldScope {
    private final QueryContext context;

    @Inject
    public QueryExtWorldScope(@NonNull final QueryContext context) {
        this.context = Objects.requireNonNull(context);
    }

    @NonNull
    @Override
    public ExtWorldScope begin() {
        return null;
    }

    @Override
    public void commit() {

    }

    @Override
    public void revert() {

    }

    @Override
    public ContractStateStore getStore() {
        return null;
    }

    @Override
    public long peekNextEntityNumber() {
        return 0;
    }

    @Override
    public long useNextEntityNumber() {
        return 0;
    }

    @NonNull
    @Override
    public Bytes entropy() {
        return null;
    }

    @Override
    public long lazyCreationCostInGas() {
        return 0;
    }

    @Override
    public long valueInTinybars(long tinycents) {
        return 0;
    }

    @Override
    public void collectFee(@NonNull AccountID payerId, long amount) {

    }

    @Override
    public void refundFee(@NonNull AccountID payerId, long amount) {

    }

    @Override
    public void chargeStorageRent(long contractNumber, long amount, boolean itemizeStoragePayments) {

    }

    @Override
    public void updateStorageMetadata(long contractNumber, @Nullable Bytes firstKey, int slotsUsed) {

    }

    @Override
    public void createContract(long number, long parentNumber, long nonce, @Nullable Bytes evmAddress) {

    }

    @Override
    public void deleteAliasedContract(@NonNull Bytes evmAddress) {

    }

    @Override
    public void deleteUnaliasedContract(long number) {

    }

    @Override
    public List<Long> getModifiedAccountNumbers() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public List<ContractID> getCreatedContractIds() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Map<ContractID, Long> getUpdatedContractNonces() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public int getOriginalSlotsUsed(long contractNumber) {
        return 0;
    }
}
