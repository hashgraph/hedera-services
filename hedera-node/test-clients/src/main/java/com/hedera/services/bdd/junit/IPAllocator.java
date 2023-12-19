package com.hedera.services.bdd.junit;

import java.util.function.Function;

@FunctionalInterface
public interface IPAllocator extends Function<Integer, String> {
}
