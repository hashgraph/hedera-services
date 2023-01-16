package com.hedera.node.app.service.mono.txns.crypto.helpers;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.unaliased;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;

public class CryptoDeletionLogic {
  private final HederaLedger ledger;
  private final SigImpactHistorian sigImpactHistorian;
  private final AliasManager aliasManager;

  private AccountID beneficiary;

  @Inject
  public CryptoDeletionLogic(final HederaLedger ledger, final SigImpactHistorian sigImpactHistorian, final AliasManager aliasManager) {
    this.ledger = ledger;
    this.sigImpactHistorian = sigImpactHistorian;
    this.aliasManager = aliasManager;
  }

  public AccountID performCryptoDeleteFor(final CryptoDeleteTransactionBody op) {
    beneficiary = null;
    AccountID id = op.getDeleteAccountID();
    validateFalse(ledger.isKnownTreasury(id), ACCOUNT_IS_TREASURY);

    beneficiary = op.getTransferAccountID();
    validateFalse(ledger.isDetached(id), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    validateFalse(ledger.isDetached(beneficiary), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

    ledger.delete(id, beneficiary);
    sigImpactHistorian.markEntityChanged(id.getAccountNum());

    final var aliasIfAny = ledger.alias(id);
    if (!aliasIfAny.isEmpty()) {
      ledger.clearAlias(id);
      aliasManager.unlink(aliasIfAny);
      sigImpactHistorian.markAliasChanged(aliasIfAny);
    }
    return id;
  }

  public ResponseCodeEnum validate(CryptoDeleteTransactionBody op) {
    if (!op.hasDeleteAccountID() || !op.hasTransferAccountID()) {
      return ACCOUNT_ID_DOES_NOT_EXIST;
    }

    if (op.getDeleteAccountID().equals(op.getTransferAccountID())) {
      return TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
    }

    return OK;
  }

  public AccountID getLastBeneficiary() {
    return beneficiary;
  }
}
