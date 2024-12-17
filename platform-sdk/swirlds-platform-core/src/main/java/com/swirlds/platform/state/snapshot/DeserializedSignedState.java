/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.snapshot;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.signed.ReservedSignedState;

/**
 * This record encapsulates the data read from a new signed state.
 *
 * @param reservedSignedState
 * 		the signed state that was loaded
 * @param originalHash
 * 		the hash of the signed state when it was serialized, may not be the same as the current hash
 */
public record DeserializedSignedState(ReservedSignedState reservedSignedState, Hash originalHash) {}
