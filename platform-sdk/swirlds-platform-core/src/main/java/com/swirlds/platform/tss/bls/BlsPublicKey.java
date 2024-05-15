/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.tss.bls;

import com.swirlds.platform.tss.TssPublicKey;
import com.swirlds.platform.tss.blscrypto.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A BLS implementation of a TSS public key.
 *
 * @param keyMaterial the public key material
 */
public record BlsPublicKey(@NonNull GroupElement keyMaterial) implements TssPublicKey {}
