package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.Utils.asHederaKey;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.NotImplementedException;

@SuppressWarnings("DanglingJavadoc")
public class ConsensusPreTransactionHandlerImpl implements
    com.hedera.node.app.service.consensus.ConsensusPreTransactionHandler {

  private final AccountStore accountStore;

  public ConsensusPreTransactionHandlerImpl(@Nonnull final AccountStore accountStore) {
    this.accountStore = accountStore;
  }

  @Override
  /** {@inheritDoc} */
  public TransactionMetadata preHandleCreateTopic(TransactionBody txn) {
    final var op = txn.getConsensusCreateTopic();
    final var payer = txn.getTransactionID().getAccountID();

    final var adminKey = asHederaKey(op.getAdminKey());
    final var submitKey = asHederaKey(op.getSubmitKey());
    if (adminKey.isPresent() || submitKey.isPresent()) {
      final var otherReqs = new ArrayList<HederaKey>();
      adminKey.ifPresent(otherReqs::add);
      submitKey.ifPresent(otherReqs::add);
      return new SigTransactionMetadata(accountStore, txn, payer, otherReqs);
    }

    return new SigTransactionMetadata(accountStore, txn, payer);
  }

  @Override
  /** {@inheritDoc} */
  public TransactionMetadata preHandleUpdateTopic(TransactionBody txn) {
    throw new NotImplementedException();
  }

  @Override
  /** {@inheritDoc} */
  public TransactionMetadata preHandleDeleteTopic(TransactionBody txn) {
    throw new NotImplementedException();
  }

  @Override
  /** {@inheritDoc} */
  public TransactionMetadata preHandleSubmitMessage(TransactionBody txn) {
    throw new NotImplementedException();
  }
}
