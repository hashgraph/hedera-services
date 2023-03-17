/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.dispatch;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.Mutable;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.dispatch.flowchart.DispatchFlowchart;
import com.swirlds.platform.dispatch.types.TriggerEight;
import com.swirlds.platform.dispatch.types.TriggerFive;
import com.swirlds.platform.dispatch.types.TriggerFour;
import com.swirlds.platform.dispatch.types.TriggerNine;
import com.swirlds.platform.dispatch.types.TriggerOne;
import com.swirlds.platform.dispatch.types.TriggerSeven;
import com.swirlds.platform.dispatch.types.TriggerSix;
import com.swirlds.platform.dispatch.types.TriggerTen;
import com.swirlds.platform.dispatch.types.TriggerThree;
import com.swirlds.platform.dispatch.types.TriggerTwo;
import com.swirlds.platform.dispatch.types.TriggerZero;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the construction of dispatch methods. Useful for linking together various platform
 * components with minimal performance overhead.
 */
public class DispatchBuilder implements Mutable, Startable {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final Runnable MUTABILITY_GUARD = () -> {
        throw new MutabilityException("no dispatch is permitted prior to the dispatcher being started");
    };

    private boolean immutable = false;

    private final Map<Class<? extends Trigger<?>>, List<Trigger<?>>> observers = new HashMap<>();

    private final DispatchFlowchart flowchart;

    private static final Path FLOWCHART_LOCATION = Path.of("platform-components.mermaid");

    /**
     * Create a new dispatch builder.
     *
     * @param configuration
     * 		dispatch configuration
     */
    public DispatchBuilder(final DispatchConfiguration configuration) {
        throwArgNull(configuration, "configuration");
        if (configuration.flowchartEnabled()) {
            flowchart = new DispatchFlowchart(configuration);
        } else {
            flowchart = null;
        }
    }

    /**
     * <p>
     * Register a new observer. Multiple observers for the same type of dispatch event may be registered.
     * </p>
     *
     * <p>
     * May only be called before {@link #start()} is invoked.
     * </p>
     *
     * <p>
     * It is thread safe to leak a reference to "this" in a constructor via this method, since observers are not
     * permitted to be used until after the dispatch builder has been sealed.
     * </p>
     *
     * @param owner
     * 		the object (or the class of the object) that "owns" the observer. This
     * 		information is used only for generating documentation, and does not affect the routing of dispatches.
     * 		It is safe to pass "this" in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the type of the trigger
     * @param observer
     * 		the observer
     * @param <BASE_INTERFACE>
     * 		the base functional interface for the trigger,
     * 		e.g. {@link TriggerZero}, {@link TriggerOne}, etc.
     * @param <TRIGGER_CLASS>
     * 		a specific trigger type, should inherit from the BASE_INTERFACE
     * @return this object
     * @throws com.swirlds.common.exceptions.MutabilityException
     * 		if called after {@link #start()}
     */
    public <BASE_INTERFACE extends Trigger<BASE_INTERFACE>, TRIGGER_CLASS extends BASE_INTERFACE>
            DispatchBuilder registerObserver(
                    final Object owner, final Class<TRIGGER_CLASS> triggerClass, final BASE_INTERFACE observer) {

        registerObserver(owner, triggerClass, observer, null);
        return this;
    }

    /**
     * <p>
     * Register a new observer. Multiple observers for the same type of dispatch event may be registered.
     * </p>
     *
     * <p>
     * May only be called before {@link #start()} is invoked.
     * </p>
     *
     * <p>
     * It is thread safe to leak a reference to "this" in a constructor via this method, since observers are not
     * permitted to be used until after the dispatch builder has been sealed.
     * </p>
     *
     * @param owner
     * 		the object (or the class of the object) that "owns" the observer. This
     * 		information is used only for generating documentation, and does not affect the routing of dispatches.
     * 		It is safe to pass "this" in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the type of the trigger
     * @param observer
     * 		the observer
     * @param comment
     * 		a comment used to enhance the dispatch flowchart
     * @param <BASE_INTERFACE>
     * 		the base functional interface for the trigger,
     * 		e.g. {@link TriggerZero}, {@link TriggerOne}, etc.
     * @param <TRIGGER_CLASS>
     * 		a specific trigger type, should inherit from the BASE_INTERFACE
     * @return this object
     * @throws com.swirlds.common.exceptions.MutabilityException
     * 		if called after {@link #start()}
     */
    public <BASE_INTERFACE extends Trigger<BASE_INTERFACE>, TRIGGER_CLASS extends BASE_INTERFACE>
            DispatchBuilder registerObserver(
                    final Object owner,
                    final Class<TRIGGER_CLASS> triggerClass,
                    final BASE_INTERFACE observer,
                    final String comment) {

        throwIfImmutable("observer can only be registered while this object is mutable");
        throwArgNull(triggerClass, "triggerClass");
        throwArgNull(triggerClass, "dispatchType");
        throwArgNull(observer, "observer");

        if (flowchart != null && isMutable()) {
            flowchart.registerObserver(owner, triggerClass, comment);
        }

        getObserverList(triggerClass).add(observer);

        return this;
    }

    /**
     * Register all of an object's public observer methods annotated with {@link Observer}.
     * This is a convenience method -- it's perfectly acceptable to register each observer
     * one at a time via {@link #registerObserver(Object, Class, Trigger)}.
     *
     * @param object
     * 		the object with observers
     * @return this object
     */
    public DispatchBuilder registerObservers(final Object object) {
        throwIfImmutable("observers can only be registered while this object is mutable");
        throwArgNull(object, "object");

        for (final Method method : object.getClass().getDeclaredMethods()) {

            final Observer annotation = method.getAnnotation(Observer.class);
            if (annotation == null) {
                continue;
            }

            if (annotation.value().length == 0) {
                throw new IllegalArgumentException("No triggers specified. At least one trigger type "
                        + "must be passed to each @Observer annotation.");
            }

            final String comment = annotation.comment();

            for (final Class<? extends Trigger<?>> triggerClass : annotation.value()) {
                registerAnnotatedClassMethod(object, method, triggerClass, comment);
            }
        }

        return this;
    }

    /**
     * Register an annotated class member function as an observer.
     *
     * @param object
     * 		the object that is the observer
     * @param method
     * 		the method that should be called when the dispatch is triggered
     * @param triggerClass
     * 		the type of the trigger
     * @param comment
     * 		a comment used to enhance the dispatch flowchart
     */
    private void registerAnnotatedClassMethod(
            final Object object,
            final Method method,
            final Class<? extends Trigger<?>> triggerClass,
            final String comment) {
        try {
            final MethodType factoryType = MethodType.methodType(triggerClass, object.getClass());
            final MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            final MethodType genericMethodType = methodType.generic().changeReturnType(methodType.returnType());
            final MethodHandle target = LOOKUP.unreflect(method);

            final Trigger<?> trigger = (Trigger<?>) LambdaMetafactory.metafactory(
                            LOOKUP, "dispatch", factoryType, genericMethodType, target, methodType)
                    .getTarget()
                    .bindTo(object)
                    .invoke();

            getObserverList(triggerClass).add(trigger);

            if (flowchart != null && isMutable()) {
                flowchart.registerObserver(object, triggerClass, comment);
            }

        } catch (final Throwable e) {
            // factoryHandle.invoke() forces us to catch Throwable. >:( It doesn't really matter
            // what this throws, if anything at all fails we can't recover.
            throw new RuntimeException("unable to register observer " + object.getClass() + "." + method.getName(), e);
        }
    }

    /**
     * Get a dispatcher method for a given type. Will call into all registered observers for this type,
     * even those registered after this dispatcher is returned. Method returned is a no-op if no observers
     * are ever registered. The dispatcher returned will throw a mutability exception if invoked prior to
     * {@link #start()} being called.
     *
     * @param owner
     * 		the object (or the class of the object) that "owns" the dispatcher.
     * 		This information is used only for generating documentation, and does not affect the routing of dispatches.
     * 		It is safe to pass "this" in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the type of the dispatch event
     * @param <BASE_INTERFACE>
     * 		the base functional interface for the dispatcher,
     * 		e.g. {@link TriggerZero}, {@link TriggerOne}, etc.
     * @param <DISPATCHER_TYPE>
     * 		a specific dispatcher type, should inherit from the BASE_INTERFACE
     * @return a dispatch method, not null even if no observers have been registered for this event
     */
    public <BASE_INTERFACE extends Trigger<BASE_INTERFACE>, DISPATCHER_TYPE extends BASE_INTERFACE>
            BASE_INTERFACE getDispatcher(final Object owner, final Class<DISPATCHER_TYPE> triggerClass) {
        return getDispatcher(owner, triggerClass, null);
    }

    /**
     * Get a dispatcher method for a given type. Will call into all registered observers for this type,
     * even those registered after this dispatcher is returned. Method returned is a no-op if no observers
     * are ever registered. The dispatcher returned will throw a mutability exception if invoked prior to
     * {@link #start()} being called.
     *
     * @param owner
     * 		the object (or the class of the object) that "owns" the dispatcher.
     * 		This information is used only for generating documentation, and does not affect the routing of dispatches.
     * 		It is safe to pass "this" in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the type of the dispatch event
     * @param <BASE_INTERFACE>
     * 		the base functional interface for the dispatcher,
     * 		e.g. {@link TriggerZero}, {@link TriggerOne}, etc.
     * @param <DISPATCHER_TYPE>
     * 		a specific dispatcher type, should inherit from the BASE_INTERFACE
     * @param comment
     * 		a comment on how the dispatch is being used, used to enhance the dispatch flowchart
     * @return a dispatch method, not null even if no observers have been registered for this event
     */
    @SuppressWarnings("unchecked")
    public <BASE_INTERFACE extends Trigger<BASE_INTERFACE>, DISPATCHER_TYPE extends BASE_INTERFACE>
            BASE_INTERFACE getDispatcher(
                    final Object owner, final Class<DISPATCHER_TYPE> triggerClass, final String comment) {

        throwArgNull(owner, "owner");
        throwArgNull(triggerClass, "dispatchType");

        if (flowchart != null && isMutable()) {
            flowchart.registerDispatcher(owner, triggerClass, comment);
        }

        final List<Trigger<?>> observerList = getObserverList(triggerClass);

        if (TriggerZero.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerZero) () -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerZero) observer).dispatch();
                }
            };
        } else if (TriggerOne.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerOne<Object>) (a) -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerOne<Object>) observer).dispatch(a);
                }
            };
        } else if (TriggerTwo.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerTwo<Object, Object>) (a, b) -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerTwo<Object, Object>) observer).dispatch(a, b);
                }
            };
        } else if (TriggerThree.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerThree<Object, Object, Object>) (a, b, c) -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerThree<Object, Object, Object>) observer).dispatch(a, b, c);
                }
            };
        } else if (TriggerFour.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerFour<Object, Object, Object, Object>) (a, b, c, d) -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerFour<Object, Object, Object, Object>) observer).dispatch(a, b, c, d);
                }
            };
        } else if (TriggerFive.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerFive<Object, Object, Object, Object, Object>) (a, b, c, d, e) -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerFive<Object, Object, Object, Object, Object>) observer).dispatch(a, b, c, d, e);
                }
            };
        } else if (TriggerSix.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerSix<Object, Object, Object, Object, Object, Object>) (a, b, c, d, e, f) -> {
                for (final Trigger<?> observer : observerList) {
                    ((TriggerSix<Object, Object, Object, Object, Object, Object>) observer).dispatch(a, b, c, d, e, f);
                }
            };
        } else if (TriggerSeven.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE)
                    (TriggerSeven<Object, Object, Object, Object, Object, Object, Object>) (a, b, c, d, e, f, g) -> {
                        for (final Trigger<?> observer : observerList) {
                            ((TriggerSeven<Object, Object, Object, Object, Object, Object, Object>) observer)
                                    .dispatch(a, b, c, d, e, f, g);
                        }
                    };
        } else if (TriggerEight.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerEight<Object, Object, Object, Object, Object, Object, Object, Object>)
                    (a, b, c, d, e, f, g, h) -> {
                        for (final Trigger<?> observer : observerList) {
                            ((TriggerEight<Object, Object, Object, Object, Object, Object, Object, Object>) observer)
                                    .dispatch(a, b, c, d, e, f, g, h);
                        }
                    };
        } else if (TriggerNine.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE) (TriggerNine<
                            Object, Object, Object, Object, Object, Object, Object, Object, Object>)
                    (a, b, c, d, e, f, g, h, i) -> {
                        for (final Trigger<?> observer : observerList) {
                            ((TriggerNine<Object, Object, Object, Object, Object, Object, Object, Object, Object>)
                                            observer)
                                    .dispatch(a, b, c, d, e, f, g, h, i);
                        }
                    };
        } else if (TriggerTen.class.isAssignableFrom(triggerClass)) {
            return (BASE_INTERFACE)
                    (TriggerTen<Object, Object, Object, Object, Object, Object, Object, Object, Object, Object>)
                            (a, b, c, d, e, f, g, h, i, j) -> {
                                for (final Trigger<?> observer : observerList) {
                                    ((TriggerTen<
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object,
                                                            Object>)
                                                    observer)
                                            .dispatch(a, b, c, d, e, f, g, h, i, j);
                                }
                            };
        } else {
            throw new IllegalStateException("unhandled dispatch type " + triggerClass);
        }
    }

    /**
     * Get a list of observers for a given trigger type. If that dispatch type does not yet have a list of observers
     * then create one and insert it into the map of observer lists.
     *
     * @param triggerClass
     * 		the type of trigger
     * @return a list of observers for the trigger type, calling this method more than once for the same
     * 		trigger type always returns the same list instance
     */
    private List<Trigger<?>> getObserverList(final Class<? extends Trigger<?>> triggerClass) {
        final List<Trigger<?>> observerList = observers.get(triggerClass);
        if (observerList != null) {
            return observerList;
        }

        final List<Trigger<?>> newObserverList = new ArrayList<>();

        if (isMutable()) {
            // Add a special observer that will cause premature dispatch to throw. This observer
            // is removed when the dispatch builder is started. Performance wise, this is superior
            // to the addition of an "if (boolean)" guard, since this extra lambda function has
            // zero performance impact after boot time.
            addMutabilityGuard(triggerClass, newObserverList);
        }

        observers.put(triggerClass, newObserverList);
        return newObserverList;
    }

    /**
     * Add a special observer that will cause premature dispatch to throw. This observer
     * is removed when the dispatch builder is started. Performance wise, this is superior
     * to the addition of an "if (boolean)" guard, since this extra lambda function has
     * zero performance impact after boot time.
     *
     * @param triggerClass
     * 		the trigger class
     * @param newObserverList
     * 		the list of observers for the dispatcher
     */
    @SuppressWarnings("Convert2MethodRef")
    private static void addMutabilityGuard(
            final Class<? extends Trigger<?>> triggerClass, final List<Trigger<?>> newObserverList) {

        if (TriggerZero.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerZero) () -> MUTABILITY_GUARD.run());
        } else if (TriggerOne.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerOne<?>) (a) -> MUTABILITY_GUARD.run());
        } else if (TriggerTwo.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerTwo<?, ?>) (a, b) -> MUTABILITY_GUARD.run());
        } else if (TriggerThree.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerThree<?, ?, ?>) (a, b, c) -> MUTABILITY_GUARD.run());
        } else if (TriggerFour.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerFour<?, ?, ?, ?>) (a, b, c, d) -> MUTABILITY_GUARD.run());
        } else if (TriggerFive.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerFive<?, ?, ?, ?, ?>) (a, b, c, d, e) -> MUTABILITY_GUARD.run());
        } else if (TriggerSix.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerSix<?, ?, ?, ?, ?, ?>) (a, b, c, d, e, f) -> MUTABILITY_GUARD.run());
        } else if (TriggerSeven.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerSeven<?, ?, ?, ?, ?, ?, ?>) (a, b, c, d, e, f, g) -> MUTABILITY_GUARD.run());
        } else if (TriggerEight.class.isAssignableFrom(triggerClass)) {
            newObserverList.add(
                    (TriggerEight<?, ?, ?, ?, ?, ?, ?, ?>) (a, b, c, d, e, f, g, h) -> MUTABILITY_GUARD.run());
        } else if (TriggerNine.class.isAssignableFrom(triggerClass)) {
            newObserverList.add(
                    (TriggerNine<?, ?, ?, ?, ?, ?, ?, ?, ?>) (a, b, c, d, e, f, g, h, i) -> MUTABILITY_GUARD.run());
        } else if (TriggerTen.class.isAssignableFrom(triggerClass)) {
            newObserverList.add((TriggerTen<?, ?, ?, ?, ?, ?, ?, ?, ?, ?>)
                    (a, b, c, d, e, f, g, h, i, j) -> MUTABILITY_GUARD.run());
        } else {
            throw new IllegalStateException("unhandled dispatch type " + triggerClass);
        }
    }

    /**
     * Once started, dispatchers are permitted to start invoking callbacks.
     *
     * @throws MutabilityException
     * 		if called more than once
     */
    @Override
    public void start() {
        throwIfImmutable("start() should only be called once");
        immutable = true;

        // Remove the preventPrematureDispatch() lambda that was added to each observer list.
        // The implementation for observers is ArrayList. It's mildly less efficient to remove the first
        // element of an array list, for lists of this size. However, since we only pay that cost at boot time,
        // it's much better to go with an array list over a linked list for the enhanced runtime performance.
        // Iterating over an array list is more efficient than iterating over a linked list. Although the
        // difference may seem small, this code is used in performance critical areas.
        for (final List<Trigger<?>> observerList : observers.values()) {
            observerList.remove(0);
        }

        if (flowchart != null) {
            try {
                flowchart.writeFlowchart(FLOWCHART_LOCATION);
            } catch (final IOException e) {
                throw new UncheckedIOException("unable to generate dispatch flowchart", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return immutable;
    }
}
