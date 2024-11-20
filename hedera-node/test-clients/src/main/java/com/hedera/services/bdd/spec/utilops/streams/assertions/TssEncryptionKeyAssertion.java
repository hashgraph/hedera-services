package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TssEncryptionKeyAssertion implements BlockStreamAssertion {
    private final HapiSpec spec;

    public TssEncryptionKeyAssertion(@NonNull HapiSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean test(@NonNull Block block) throws AssertionError {
        for (var blockItem : block.items()) {
            if (blockItem.hasEventTransaction()){
                if (blockItem.eventTransaction().hasApplicationTransaction()) {
                    final var applicationTxn = blockItem.eventTransaction().applicationTransaction();
                    try {
                        TransactionBody txnBody = TransactionBody.parseFrom(applicationTxn.toByteArray());
                        if (txnBody.hasTssEncryptionKey()) {
                            return true;
                        }
                    } catch (InvalidProtocolBufferException e) {
                        throw new AssertionError("Error parsing transaction body");
                    }
                }
            }
        }

        return false;
    }
}