package com.hedera.services.bdd.spec.utilops.streams.assertions;

import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_FIRST_NODE_ACCOUNT_NUM;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Assertions;

public class TssEncryptionKeyAssertion implements BlockStreamAssertion {
    private final HapiSpec spec;

    private int actualTssEncryptionKeyTxns = 0;

    public TssEncryptionKeyAssertion(@NonNull HapiSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean test(@NonNull Block block) throws AssertionError {
        observeInteractionsIn(block);
        int expectedTssEncryptionKeyTxns = 4;
        if (actualTssEncryptionKeyTxns != expectedTssEncryptionKeyTxns) {
            if (actualTssEncryptionKeyTxns > expectedTssEncryptionKeyTxns) {
                Assertions.fail(
                        "Too many TSS Encryption Key txns submitted, expected " + expectedTssEncryptionKeyTxns + " but got " + actualTssEncryptionKeyTxns);
            }
            return actualTssEncryptionKeyTxns == expectedTssEncryptionKeyTxns;
        }
        return false;
    }

    private void observeInteractionsIn(@NonNull final Block block) {
        for (final var item : block.items()) {
            if (item.hasEventTransaction()) {
                try {
                    final var wrapper = Transaction.PROTOBUF.parse(
                            item.eventTransactionOrThrow().applicationTransactionOrThrow());
                    final var signedTxn = SignedTransaction.PROTOBUF.parse(wrapper.signedTransactionBytes());
                    final var body = com.hedera.hapi.node.transaction.TransactionBody.PROTOBUF.parse(signedTxn.bodyBytes());
                    if (body.nodeAccountIDOrElse(AccountID.DEFAULT).accountNumOrElse(0L)
                            == CLASSIC_FIRST_NODE_ACCOUNT_NUM) {
                        if (body.hasTssEncryptionKey()) {
                            actualTssEncryptionKeyTxns++;
                        }
                    }
                } catch (ParseException e) {
                    Assertions.fail(e.getMessage());
                }
            }
        }
    }
}