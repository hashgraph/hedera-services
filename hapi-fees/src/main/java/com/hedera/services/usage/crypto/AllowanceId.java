package com.hedera.services.usage.crypto;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.jetbrains.annotations.NotNull;

public record AllowanceId(Long tokenNum, Long spenderNum) implements Comparable<AllowanceId>{
	@Override
	public int compareTo(@NotNull final AllowanceId that) {
		return new CompareToBuilder()
				.append(tokenNum, that.tokenNum)
				.append(spenderNum, that.spenderNum)
				.toComparison();
	}
}
