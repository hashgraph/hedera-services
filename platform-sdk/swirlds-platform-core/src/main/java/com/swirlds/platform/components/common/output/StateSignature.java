/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.common.output;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;

/**
 * A node's signature for a round's state hash.
 *
 * @param round
 * 		the round of the state this signature applies to
 * @param signerId
 * 		the id of the signing node
 * @param stateHash
 * 		the hash of the state that is signed
 * @param signature
 * 		the signature of the stateHash
 */
public record StateSignature(long round, long signerId, Hash stateHash, Signature signature) {}
