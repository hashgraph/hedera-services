/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.pricing;

/** Represents the eight kinds of resources that may be consumed in the Hedera network. */
public enum UsableResource {
    /** Various types of fixed overhead. */
    CONSTANT,
    /** Consensus network bandwidth in bytes. */
    BPT,
    /** Signature verification. */
    VPT,
    /** Memory storage in byte-hours. */
    RBH,
    /** Disk storage in byte-hours. */
    SBH,
    /** EVM computation. */
    GAS,
    /** Node-specific bandwidth used to serve data from memory, in bytes. */
    BPR,
    /** Node-specific bandwidth used to serve data from disk, in bytes. */
    SBPR,
}
