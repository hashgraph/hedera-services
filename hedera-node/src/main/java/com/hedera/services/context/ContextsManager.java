package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
 * Defines a type that manages references to multiple node contexts per
 * classloader by using the {@link com.swirlds.common.Platform#getSelfId()} in the context.
 *
 * Logically, the {@link ServicesContext} is a singleton but for testing
 * it is convenient to run multiple nodes in the same JVM.
 *
 * @author Michael Tinker
 */
public interface ContextsManager {
	/**
	 * Forgets all node contexts stored up to this point.
	 */
	void clear();

	/**
	 * Stores a node context based on how it self-identifies.
	 *
	 * @param ctx the context to store
	 */
	void store(ServicesContext ctx);

	/**
	 * Returns true if the node's context has already been initialized.
	 *
	 * @param nodeId which node to check
	 */
	boolean isInitialized(long nodeId);

	/**
	 * Gets a reference to a context by platform id.
	 *
	 * @param nodeId the id of the context to get
	 * @return the designated context
	 * @throws com.hedera.services.exceptions.ContextNotFoundException if there is no such context
	 */
	ServicesContext lookup(long nodeId);
}
