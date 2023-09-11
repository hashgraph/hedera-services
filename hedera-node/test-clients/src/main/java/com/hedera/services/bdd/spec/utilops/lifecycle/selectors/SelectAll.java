package com.hedera.services.bdd.spec.utilops.lifecycle.selectors;

import com.hedera.services.bdd.junit.HapiTestNode;
import edu.umd.cs.findbugs.annotations.NonNull;

public class SelectAll implements NodeSelector {
    @Override
    public boolean test(@NonNull final HapiTestNode hapiTestNode) {
        return true;
    }

    @Override
    public String toString() {
        return "including all nodes";
    }
}
