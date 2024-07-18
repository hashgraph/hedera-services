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

package com.swirlds.crypto.pairings.api;

/**
 * An enumeration of supported pairing curves.
 */
public enum Curve {
    /**
     * Alt-BN128
     * Also known as BN254.
     * r=21888242871839275222246405745257275088548364400416034343698204186575808495617
     * p=21888242871839275222246405745257275088548364400416034343698204186575808495617
     * q=21888242871839275222246405745257275088696311157297823662689037894645226208583
     * Generator 5.
     */
    ALT_BN128,
}
