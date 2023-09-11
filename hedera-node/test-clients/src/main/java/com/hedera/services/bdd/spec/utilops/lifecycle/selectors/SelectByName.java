package com.hedera.services.bdd.spec.utilops.lifecycle.selectors;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.HapiTestNode;
import edu.umd.cs.findbugs.annotations.NonNull;

public class SelectByName implements NodeSelector {
    private final String name;

    public SelectByName(@NonNull final String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public boolean test(@NonNull final HapiTestNode hapiTestNode) {
        return name.equalsIgnoreCase(hapiTestNode.getName());
    }

    @Override
    public String toString() {
        return "by name '" + name + "'";
    }
}
