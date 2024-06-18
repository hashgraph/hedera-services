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

package com.hedera.node.app.workflows.handle.flow.txn.modules;

import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchComponent;
import dagger.Module;

/**
 * The module that adds the user dispatch and child dispatch subcomponents under user transaction component.
 */
@Module(subcomponents = {UserDispatchComponent.class, ChildDispatchComponent.class})
public interface DispatchSubcomponentsModule {}
