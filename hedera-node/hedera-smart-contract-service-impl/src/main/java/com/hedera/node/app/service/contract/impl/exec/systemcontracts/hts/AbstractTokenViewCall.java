package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;

public abstract class AbstractTokenViewCall extends AbstractHtsCall {
    @Nullable
    private final Token token;

    protected AbstractTokenViewCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token) {
        super(enhancement);
        this.token = token;
    }

    @Override
    public @NonNull PricedResult execute() {
        // TODO - gas calculation
        if (token == null) {
            return gasOnly(revertResult(INVALID_TOKEN_ID, 0L));
        } else {
            return gasOnly(resultOfViewingToken(token));
        }
    }

    protected abstract HederaSystemContract.FullResult resultOfViewingToken(@NonNull Token token);
}
