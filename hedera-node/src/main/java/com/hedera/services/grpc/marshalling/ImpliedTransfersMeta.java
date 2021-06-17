package com.hedera.services.grpc.marshalling;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldTransaction;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.time.Instant;

/**
 * Encapsulates the validity of a CryptoTransfer transaction, given a choice of
 * two parameters: the maximum allowed number of ℏ adjustments, and the maximum
 * allowed number of token unit adjustments.
 *
 * Note that we need to remember these two parameters in order to safely reuse
 * this validation across "span" between the {@link com.hedera.services.ServicesState#expandSignatures(SwirldTransaction)}
 * and {@link com.hedera.services.ServicesState#handleTransaction(long, boolean, Instant, Instant, SwirldTransaction, SwirldDualState)}
 * callbacks.
 *
 * This is because either parameter <i>could</i> change due to an update of
 * file 0.0.121 between the two callbacks. So we have to double-check that
 * neither <i>did</i> change before reusing the work captured by this
 * validation result.
 */
public class ImpliedTransfersMeta {
	private final long maxExplicitHbarAdjusts;
	private final long maxExplicitTokenAdjusts;
	private final ResponseCodeEnum code;

	public ImpliedTransfersMeta(
			long maxExplicitHbarAdjusts,
			long maxExplicitTokenAdjusts,
			ResponseCodeEnum code
	) {
		this.code = code;
		this.maxExplicitHbarAdjusts = maxExplicitHbarAdjusts;
		this.maxExplicitTokenAdjusts = maxExplicitTokenAdjusts;
	}

	public boolean wasDerivedFrom(GlobalDynamicProperties dynamicProperties) {
		return maxExplicitHbarAdjusts == dynamicProperties.maxTransferListSize() &&
				maxExplicitTokenAdjusts == dynamicProperties.maxTokenTransferListSize();
	}

	public long getMaxExplicitHbarAdjusts() {
		return maxExplicitHbarAdjusts;
	}

	public long getMaxExplicitTokenAdjusts() {
		return maxExplicitTokenAdjusts;
	}

	public ResponseCodeEnum code() {
		return code;
	}

	/* NOTE: The object methods below are only overridden to improve
			readability of unit tests; this model object is not used in hash-based
			collections, so the performance of these methods doesn't matter. */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(ImpliedTransfersMeta.class)
				.add("code", code)
				.add("maxExplicitHbarAdjusts", maxExplicitHbarAdjusts)
				.add("maxExplicitTokenAdjusts", maxExplicitTokenAdjusts)
				.toString();
	}
}
