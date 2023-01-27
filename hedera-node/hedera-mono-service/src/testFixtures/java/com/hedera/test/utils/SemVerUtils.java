package com.hedera.test.utils;

import com.hederahashgraph.api.proto.java.SemanticVersion;

public class SemVerUtils {
    private SemVerUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static SemanticVersion standardSemverWith(final int major, final int minor, final int patch) {
        return SemanticVersion.newBuilder()
                .setMajor(major)
                .setMinor(minor)
                .setPatch(patch)
                .build();
    }
}
