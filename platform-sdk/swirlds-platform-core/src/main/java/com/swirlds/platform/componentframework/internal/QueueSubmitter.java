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

package com.swirlds.platform.componentframework.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;

/**
 * A dynamic proxy that submits tasks to a queue for any method that gets called
 */
public class QueueSubmitter implements InvocationHandler {
    private final BlockingQueue<Object> queue;

    private QueueSubmitter(final BlockingQueue<Object> queue) {
        this.queue = queue;
    }

    /**
     * Create a new {@link QueueSubmitter} for the given class and queue
     *
     * @param clazz
     * 		the class to create a submitter for
     * @param queue
     * 		the queue to submit tasks to
     * @return a new {@link QueueSubmitter} for the given class and queue
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(final Class<T> clazz, final BlockingQueue<Object> queue) {
        return (T) Proxy.newProxyInstance(
                QueueSubmitter.class.getClassLoader(), new Class[] {clazz}, new QueueSubmitter(queue));
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        switch (method.getName()) {
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return false;
            case "toString":
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            case "getProcessingMethods":
                throw new UnsupportedOperationException();
            default:
                queue.put(args[0]);
        }
        return null;
    }
}
