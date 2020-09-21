package com.hedera.services.usage.token;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class TokenUsageUtils {
	public static <T> long keySizeIfPresent(T op, Predicate<T> check, Function<T, Key> getter) {
		return check.test(op)? getAccountKeyStorageSize(getter.apply(op)) : 0L;
	}

	public static long refBpt(TokenRef ref) {
		return ref.hasTokenId() ? BASIC_ENTITY_ID_SIZE : ref.getSymbolBytes().size();
	}
}
