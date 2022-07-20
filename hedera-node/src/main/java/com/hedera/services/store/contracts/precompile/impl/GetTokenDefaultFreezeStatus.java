package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenDefaultFreezeStatus extends AbstractReadOnlyPrecompile {
  private GetTokenDefaultFreezeStatusWrapper defaultFreezeStatusWrapper;

  public GetTokenDefaultFreezeStatus(
     final SyntheticTxnFactory syntheticTxnFactory,
     final WorldLedgers ledgers,
     final EncodingFacade encoder,
     final DecodingFacade decoder,
     final PrecompilePricingUtils pricingUtils) {
    super(null, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
  }

    @Override
    public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        defaultFreezeStatusWrapper = decoder.decodeTokenDefaultFreezeStatus(input);
        return super.body(input, aliasResolver);
    }

  @Override
  public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
    final var defaultFreezeStatus = ledgers.defaultFreezeStatus(defaultFreezeStatusWrapper.tokenID());
    return encoder.encodeGetTokenDefaultFreezeStatus(defaultFreezeStatus);
  }

}
