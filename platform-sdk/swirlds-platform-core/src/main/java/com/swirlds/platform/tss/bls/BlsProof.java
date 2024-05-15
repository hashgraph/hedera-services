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

import com.swirlds.platform.tss.TssProof;
import com.swirlds.platform.tss.blscrypto.FieldElement;
import com.swirlds.platform.tss.blscrypto.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A BLS implementation of a TSS proof.
 * @param f TODO
 * @param a
 * @param y
 * @param z_r
 * @param z_a
 */
public record BlsProof(
        @NonNull GroupElement f,
        @NonNull GroupElement a,
        @NonNull GroupElement y,
        @NonNull FieldElement z_r,
        @NonNull FieldElement z_a)
        implements TssProof {}
