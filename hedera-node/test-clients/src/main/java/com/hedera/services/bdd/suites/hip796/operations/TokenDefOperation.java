package com.hedera.services.bdd.suites.hip796.operations;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class TokenDefOperation extends UtilOp {
    private Set<TokenFeature> features = EnumSet.noneOf(TokenFeature.class);
    private List<DesiredAccountTokenRelation> desiredAccountTokenRelations = new ArrayList<>();

    public TokenDefOperation withFeatures(@NonNull final TokenFeature... variousFeatures) {
        requireNonNull(variousFeatures);
        features = EnumSet.copyOf(Arrays.asList(variousFeatures));
        return this;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        return false;
    }
}
