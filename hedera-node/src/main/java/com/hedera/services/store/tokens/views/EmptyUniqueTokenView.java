package com.hedera.services.store.tokens.views;

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

import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * A {@link UniqTokenView} that always returns empty lists.
 */
public enum EmptyUniqueTokenView implements UniqTokenView {
	EMPTY_UNIQUE_TOKEN_VIEW;

	@Override
	public List<TokenNftInfo> ownedAssociations(@Nonnull EntityNum owner, long start, long end) {
		return Collections.emptyList();
	}

	@Override
	public List<TokenNftInfo> typedAssociations(@Nonnull TokenID type, long start, long end) {
		return Collections.emptyList();
	}
}
