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
import java.util.List;

/**
 * Defines a type able to describe sub-lists of the unique tokens that share
 * one of two common characteristics:
 * <ol>
 *     <li>All the unique tokens belong to the same non-fungible unique token type.</li>
 *     <li>All the unique tokens have the same owner.</li>
 * </ol>
 *
 * The ordering of these sub-lists are only guaranteed stable between queries
 * if no change is made to the family of unique tokens in question.
 */
public interface UniqTokenView {
	/**
	 * Returns query-friendly descriptions of the requested sub-list of the unique
	 * tokens owned by the given account.
	 *
	 * @param owner the non-null owning account of interest
	 * @param start the inclusive, zero-based index at the start the desired sub-list
	 * @param end the exclusive, zero-based index at the end of the desired sub-list
	 * @return the (possibly empty) sub-list of the owning account's unique tokens
	 */
	List<TokenNftInfo> ownedAssociations(@Nonnull EntityNum owner, long start, long end);

	/**
	 * Returns query-friendly descriptions of the requested sub-list of the unique
	 * tokens belonging to the given type.
	 *
	 * @param type the non-null non-fungible unique token type of interest
	 * @param start the inclusive, zero-based index at the start the desired sub-list
	 * @param end the exclusive, zero-based index at the end of the desired sub-list
	 * @return the (possibly empty) sub-list of the type's unique tokens
	 */
	List<TokenNftInfo> typedAssociations(@Nonnull TokenID type, long start, long end);
}
