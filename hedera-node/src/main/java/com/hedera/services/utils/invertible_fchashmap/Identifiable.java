/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

// TODO this interface does not belong in this package
package com.hedera.services.utils.invertible_fchashmap;

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

/**
 * Describes an object with an immutable ID.
 *
 * @param <I>
 * 		an immutable object type that uniquely identifies another object type.
 */
public interface Identifiable<I> {

	/**
	 * Get an object that uniquely describes this object.
	 * Over the lifecycle of this object its identity should never change.
	 * <p>
	 * Identifiable objects that are also {@link com.swirlds.common.FastCopyable FastCopyable} are required to
	 * maintain object identifiers that are equal across all copies of a particular object. That is,
	 * {@code x.getIdentity().equals(x.copy().getIdentity())} must always evaluate to true.
	 * <p>
	 * Objects that are self identifiable (i.e. immutable objects) can use themselves as an identifier,
	 * and can simply use the default implementation provided below. All other objects must provide
	 * their own implementation of this method that returns an identity object that is not "self".
	 * <p>
	 * When implementing non-self Identifiable objects, always reuse identity objects if possible.
	 * For example, a {@link com.swirlds.common.FastCopyable FastCopyable} object that extends Identifiable
	 * can simply copy a reference from one copy to the next.
	 */
	@SuppressWarnings("unchecked")
	default I getIdentity() {
		return (I) this;
	}
}
