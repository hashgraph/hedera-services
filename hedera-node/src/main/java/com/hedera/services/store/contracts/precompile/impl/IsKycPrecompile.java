package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class IsKycPrecompile extends AbstractReadOnlyPrecompile{
    private AccountID accountId;

  public IsKycPrecompile(
      TokenID tokenId,
      SyntheticTxnFactory syntheticTxnFactory,
      WorldLedgers ledgers,
      EncodingFacade encoder,
      DecodingFacade decoder,
      PrecompilePricingUtils pricingUtils) {
    super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
  }

  @Override
  public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
    final var tokenIsKycWrapper = decoder.decodeIsKyc(input, aliasResolver);
    tokenId = tokenIsKycWrapper.token();
    accountId = tokenIsKycWrapper.account();
    return super.body(input, aliasResolver);
  }

  @Override
  public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
    final boolean isKyc = ledgers.isKyc(accountId, tokenId);
    return encoder.encodeIsKyc(isKyc);
  }
}
