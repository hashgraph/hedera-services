/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.mono.utils;

import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;

/**
 * Denotes a hollow account completion. Given the hollow account num and the key info from this record,
 * a component can finalize a hollow account.
 * @param hollowAccountNum the {@link EntityNum} corresponding to the hollow account
 * @param key the {@link JECDSASecp256k1Key} key to set for the completed account
 */
public record PendingCompletion(EntityNum hollowAccountNum, JECDSASecp256k1Key key) {}
