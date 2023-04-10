package com.swirlds.common.threading.manager;

import com.swirlds.common.utility.Lifecycle;

/**
 * A thread manager that has a start/stop lifecycle.
 */
public interface StartableThreadManager extends ThreadManager, Lifecycle {
}
