package com.hedera.services.store.contracts.precompile.impl;


import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.GrantKycLogic;
import com.hedera.services.txns.token.RevokeKycLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractGrantRevokeKycPrecompile extends AbstractWritePrecompile {
  private final boolean hasGrantKycLogic;
  protected final ContractAliases aliases;
  protected final EvmSigsVerifier sigsVerifier;
  protected GrantRevokeKycWrapper grantRevokeOp;

  protected AbstractGrantRevokeKycPrecompile(
      WorldLedgers ledgers,
      DecodingFacade decoder,
      ContractAliases aliases,
      EvmSigsVerifier sigsVerifier,
      SideEffectsTracker sideEffects,
      SyntheticTxnFactory syntheticTxnFactory,
      InfrastructureFactory infrastructureFactory,
      PrecompilePricingUtils pricingUtils,
      boolean hasGrantKycLogic) {
    super(
        ledgers,
        decoder,
        sideEffects,
        syntheticTxnFactory,
        infrastructureFactory,
        pricingUtils);
    this.aliases = aliases;
    this.sigsVerifier = sigsVerifier;
    this.hasGrantKycLogic = hasGrantKycLogic;
  }

  @Override
  public void run(MessageFrame frame) {
    Objects.requireNonNull(grantRevokeOp);

    /* --- Check required signatures --- */
    final var tokenId = Id.fromGrpcToken(grantRevokeOp.token());
    final var accountId = Id.fromGrpcAccount(grantRevokeOp.account());
    final var hasRequiredSigs =
        KeyActivationUtils.validateKey(
            frame,
            tokenId.asEvmAddress(),
            sigsVerifier::hasActiveKycKey,
            ledgers,
            aliases);
    validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

    /* --- Build the necessary infrastructure to execute the transaction --- */
    final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
    final var tokenStore =
        infrastructureFactory.newTokenStore(
            accountStore,
            sideEffects,
            ledgers.tokens(),
            ledgers.nfts(),
            ledgers.tokenRels());

    /* --- Execute the transaction and capture its results --- */
    if (hasGrantKycLogic) {
      final var grantKycLogic = infrastructureFactory.newGrantKycLogic(accountStore, tokenStore);
      executeForGrant(grantKycLogic, tokenId, accountId);
    } else {
      final var revokeKycLogic =
          infrastructureFactory.newRevokeKycLogic(accountStore, tokenStore);
      executeForRevoke(revokeKycLogic, tokenId, accountId);
    }
  }

  private void executeForGrant(GrantKycLogic grantKycLogic, Id tokenId, Id accountId) {
    validateLogic(grantKycLogic.validate(transactionBody.build()));
    grantKycLogic.grantKyc(tokenId, accountId);
  }

  private void executeForRevoke(RevokeKycLogic revokeKycLogic, Id tokenId, Id accountId) {
    validateLogic(revokeKycLogic.validate(transactionBody.build()));
    revokeKycLogic.revokeKyc(tokenId, accountId);
  }

  private void validateLogic(ResponseCodeEnum validity) {
    validateTrue(validity == OK, validity);
  }
}
