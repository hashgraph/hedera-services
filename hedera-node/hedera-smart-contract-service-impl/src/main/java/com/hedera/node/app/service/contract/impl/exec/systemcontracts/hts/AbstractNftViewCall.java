package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static java.util.Objects.requireNonNull;

public abstract class AbstractNftViewCall extends AbstractTokenViewCall {
    private final long serialNo;

    protected AbstractNftViewCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(enhancement, token);
        this.serialNo = serialNo;
    }

    @Override
    protected HederaSystemContract.FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        // TODO - gas calculation
        final var nft = nativeOperations().getNft(token.tokenIdOrThrow().tokenNum(), serialNo);
        if (nft == null) {
            return HederaSystemContract.FullResult.revertResult(INVALID_NFT_ID, 0L);
        } else {
            return resultOfViewingNft(token, nft);
        }
    }

    protected abstract HederaSystemContract.FullResult resultOfViewingNft(@NonNull Token token, @NonNull Nft nft);
}
