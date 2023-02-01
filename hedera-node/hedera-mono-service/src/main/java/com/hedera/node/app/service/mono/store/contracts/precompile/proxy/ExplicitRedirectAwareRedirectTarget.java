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
package com.hedera.node.app.service.mono.store.contracts.precompile.proxy;

import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectTarget;
import org.apache.tuweni.bytes.Bytes;

/**
 * А wrapper around the info of explicit and implicit token redirect calls.
 *
 * @param redirectTarget Contains info about the targeted function and token address.
 * @param massagedInput Populated only for explicit redirect calls --- contains the input in the
 *     implicit redirect form. See @code{ExplicitRedirectAwareDescriptorUtils} for more.
 */
public record ExplicitRedirectAwareRedirectTarget(
        RedirectTarget redirectTarget, Bytes massagedInput) {}
