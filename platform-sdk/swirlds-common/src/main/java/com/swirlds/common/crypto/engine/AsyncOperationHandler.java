/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.engine;

import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;

import com.swirlds.common.threading.futures.StandardFuture;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a generic way to process cryptographic transformations for a given {@link List} of work items in a
 * asynchronous manner on a background thread. This object also serves as the {@link java.util.concurrent.Future}
 * implementation assigned to each item contained in the {@link List}.
 *
 * @param <Element>
 * 		the type of the input to be transformed
 * @param <Provider>
 * 		the type of the {@link OperationProvider} implementation to be used
 */
public abstract class AsyncOperationHandler<Element, Provider extends OperationProvider> extends StandardFuture<Void>
        implements Runnable {

    private static final Logger logger = LogManager.getLogger(AsyncOperationHandler.class);

    private final List<Element> workItems;
    private final Provider provider;

    /**
     * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List} of items using the
     * specified algorithm provider. This method does not make a copy of the list provided and expects exclusive access
     * to the list.
     *
     * @param workItems
     * 		the list of items to be asynchronously processed by the algorithm provider
     * @param provider
     * 		the algorithm provider used to perform cryptographic transformations on each item
     */
    public AsyncOperationHandler(final List<Element> workItems, final Provider provider) {
        this(workItems, false, provider);
    }

    /**
     * Constructs an {@link AsyncOperationHandler} which will operate on the provided {@link List} of items using the
     * specified algorithm provider.
     *
     * @param workItems
     * 		the list of items to be asynchronously processed by the algorithm provider
     * @param shouldCopy
     * 		if true, then a shallow copy of the provided list will be made; otherwise the original list will be used
     * @param provider
     * 		the algorithm provider used to perform cryptographic transformations on each item
     */
    public AsyncOperationHandler(final List<Element> workItems, final boolean shouldCopy, final Provider provider) {
        super();

        if (shouldCopy) {
            this.workItems = new ArrayList<>(workItems);
        } else {
            this.workItems = workItems;
        }

        this.provider = provider;
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        for (Element item : workItems) {
            try {
                handleWorkItem(provider, item);
            } catch (RuntimeException | NoSuchAlgorithmException ex) {
                logger.warn(TESTING_EXCEPTIONS.getMarker(), "Intercepted Uncaught Exception", ex);
            }
        }

        complete(null);
    }

    /**
     * Called by the {@link #run()} method to process the cryptographic transformation for a single item on the
     * background
     * thread.
     *
     * @param provider
     * 		the algorithm provider to use
     * @param item
     * 		the input to be transformed
     * @throws NoSuchAlgorithmException
     * 		if an implementation of the required algorithm cannot be located or loaded
     */
    protected abstract void handleWorkItem(final Provider provider, final Element item) throws NoSuchAlgorithmException;
}
