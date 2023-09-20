package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.ERC_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.HAPI_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

public class GetApprovedCall extends AbstractTokenViewCall {

    private final long serialNo;
    private final boolean isErcCall;

    protected GetApprovedCall(@NonNull HederaWorldUpdater.Enhancement enhancement, @Nullable Token token, final long serialNo, final boolean isErcCall) {
        super(enhancement, token);
        this.serialNo = serialNo;
        this.isErcCall = isErcCall;
    }

    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingToken(@NonNull Token token) {
        requireNonNull(token);
        // TODO - gas calculation
        if (token.tokenType() != TokenType.NON_FUNGIBLE_UNIQUE) {
            return revertResult(INVALID_TOKEN_ID, 0L);
        }
        var spenderNum = nativeOperations().getNft(token.tokenId().tokenNum(), serialNo).spenderId().accountNumOrThrow();
        var spender = nativeOperations().getAccount(spenderNum);
        return isErcCall ? successResult(ERC_GET_APPROVED.getOutputs().encodeElements(headlongAddressOf(spender) ), 0L)
                : successResult(HAPI_GET_APPROVED.getOutputs().encodeElements(SUCCESS.getNumber(), headlongAddressOf(spender)), 0L);


    }
}
