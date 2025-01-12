/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.state.notifications;

import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchModel;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;

/**
 * Listener for fatal ISS events (i.e. of type SELF or CATASTROPHIC). This listener is ordered and asynchronous.
 * If you require ordered and synchronous dispatch that includes all ISS events, then use {@link IssListener}.
 */
@DispatchModel(mode = DispatchMode.ASYNC, order = DispatchOrder.ORDERED)
public interface AsyncFatalIssListener extends Listener<IssNotification> {}
