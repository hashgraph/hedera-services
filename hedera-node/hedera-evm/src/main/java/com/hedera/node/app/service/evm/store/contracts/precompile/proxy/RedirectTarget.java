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

package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * А wrapper around the info of explicit and implicit token redirect calls.
 *
 * @param descriptor the 4 bytes hash representation of the targeted function
 * @param token the targeted token address.
 * @param massagedInput Populated only for explicit redirect calls --- contains the input in the
 *     implicit redirect form (packed encoding). See @code{DescriptorUtils.massageInputIfNeeded()}
 *     for more.
 */
public record RedirectTarget(int descriptor, Address token, Bytes massagedInput) {}
