package com.hedera.services.bdd.junit;

import java.util.function.Function;

@FunctionalInterface
public interface PortAllocator extends Function<Integer, Integer> {
}
