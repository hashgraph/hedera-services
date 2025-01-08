package com.swirlds.platform.system.state.notifications;

import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchModel;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;

/**
 * Async listener for ISS events. If you require ordered, synchronous dispatch use {@link IssListener}.
 */
@DispatchModel(mode = DispatchMode.ASYNC, order = DispatchOrder.UNORDERED)
public interface AsyncIssListener extends Listener<IssNotification> { }
