package com.hedera.services.usage.token.meta;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenAssociateMeta extends TokenUntypedMetaBase {
	private int numOfTokens;
	private long relativeLifeTime;
	public TokenAssociateMeta(final int bpt,
			final int numOfTokens) {
		super(bpt);
		this.numOfTokens = numOfTokens;
		this.relativeLifeTime = 0;
	}

	public void setRelativeLifeTime(final long relativeLifeTime) {
		this.relativeLifeTime = relativeLifeTime;
	}

	public long getRelativeLifeTime() { return relativeLifeTime;}

	public int getNumOfTokens() { return numOfTokens; }

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper()
				.add("numOfTokens", numOfTokens)
				.add("relativeLifeTime", relativeLifeTime);
	}
}
