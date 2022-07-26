package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class TokenGetCustomFeesPrecompile extends AbstractReadOnlyPrecompile {

    private final StateView stateView;

    public TokenGetCustomFeesPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final DecodingFacade decoder,
            final PrecompilePricingUtils pricingUtils,
            final StateView stateView) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
        this.stateView = stateView;
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var tokenGetCustomFeesWrapper = decoder.decodeTokenGetCustomFees(input);
        tokenId = tokenGetCustomFeesWrapper.tokenID();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var customFees = stateView.tokenCustomFees(tokenId);
        validateTrue(customFees != null && !customFees.isEmpty(), ResponseCodeEnum.INVALID_TOKEN_ID);

        return encoder.encodeTokenGetCustomFees(customFees);
    }
}
