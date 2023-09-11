package com.hedera.services.bdd.spec.utilops.lifecycle.selectors;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.HapiTestNode;
import edu.umd.cs.findbugs.annotations.NonNull;

public class SelectByOperatorAccountId implements NodeSelector {
    private final AccountID operatorAccountId;

    public SelectByOperatorAccountId(@NonNull final AccountID operatorAccountId) {
        this.operatorAccountId = requireNonNull(operatorAccountId);
    }

    @Override
    public boolean test(@NonNull final HapiTestNode hapiTestNode) {
        return operatorAccountId.equals(hapiTestNode.getAccountId());
    }

    @Override
    public String toString() {
        final var numOrAlias = operatorAccountId.hasAccountNum()
                ? operatorAccountId.accountNumOrThrow()
                : operatorAccountId.aliasOrElse(Bytes.EMPTY);

        return "by operator accountId '"
                + operatorAccountId.shardNum() + "."
                + operatorAccountId.realmNum() + "."
                + numOrAlias + "'";
    }
}
