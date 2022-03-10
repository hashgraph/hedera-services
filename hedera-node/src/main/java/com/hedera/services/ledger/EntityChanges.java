package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.properties.BeanProperty;

import java.util.Map;

/**
 * Represents a set of changes to an entity in a {@link TransactionalLedger}.
 *
 * <b>IMPORTANT:</b>
 * <ul>
 *     <li>If the target entity is null, represents creation of a new entity with the given id and
 *     customizing changes.</li>
 *     <li>If the changes {@code Map} is null, represents removal of the entity with the given id.</li>
 * </ul>
 *
 * @param <K>
 * 		the ledger id type
 * @param <A>
 * 		the ledger entity type
 * @param <P>
 * 		the enumerable family of properties
 */
public record EntityChanges<K, A, P extends Enum<P> & BeanProperty<A>>(K id, A entity, Map<P, Object> changes) {
}
