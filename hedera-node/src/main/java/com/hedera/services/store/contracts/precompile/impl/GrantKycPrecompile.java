package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.GRANT_KYC;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GrantKycPrecompile extends AbstractGrantRevokeKycPrecompile{

  public GrantKycPrecompile(
      WorldLedgers ledgers,
      DecodingFacade decoder,
      ContractAliases aliases,
      EvmSigsVerifier sigsVerifier,
      SideEffectsTracker sideEffects,
      SyntheticTxnFactory syntheticTxnFactory,
      InfrastructureFactory infrastructureFactory,
      PrecompilePricingUtils pricingUtils,
      boolean hasGrantKycLogic) {
    super(ledgers, decoder, aliases, sigsVerifier, sideEffects, syntheticTxnFactory,
        infrastructureFactory, pricingUtils, hasGrantKycLogic);
  }

  @Override
  public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        grantRevokeOp = decoder.decodeGrantTokenKyc(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createGrantKyc(grantRevokeOp);
        return transactionBody;
  }

  @Override
  public long getMinimumFeeInTinybars(Timestamp consensusTime) {
    Objects.requireNonNull(grantRevokeOp);
        return pricingUtils.getMinimumPriceInTinybars(GRANT_KYC, consensusTime);
  }
}
