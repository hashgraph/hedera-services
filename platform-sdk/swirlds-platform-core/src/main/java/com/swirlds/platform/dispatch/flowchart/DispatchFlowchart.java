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

package com.swirlds.platform.dispatch.flowchart;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.platform.dispatch.DispatchConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class builds a mermaid flowchart showing dispatch configuration.
 */
public class DispatchFlowchart {

    private static final String INDENTATION = "    ";
    private static final String COMMENT = "%%";

    private final Set<Class<?>> uniqueObjects = new HashSet<>();
    private final Set<Class<?>> uniqueTriggers = new HashSet<>();
    private final Map<Class<?>, Set<CommentedTrigger>> dispatcherMap = new HashMap<>();
    private final Map<Class<?>, Set<CommentedTrigger>> observerMap = new HashMap<>();

    private final Set<String> triggerWhitelist;
    private final Set<String> triggerBlacklist;

    private final Set<String> objectWhitelist;
    private final Set<String> objectBlacklist;

    public DispatchFlowchart(final DispatchConfiguration dispatchConfiguration) {

        triggerWhitelist = dispatchConfiguration.getFlowchartTriggerWhitelistSet();
        triggerBlacklist = dispatchConfiguration.getFlowchartTriggerBlacklistSet();
        objectWhitelist = dispatchConfiguration.getFlowchartObjectWhitelistSet();
        objectBlacklist = dispatchConfiguration.getFlowchartObjectBlacklistSet();

        if (!triggerWhitelist.isEmpty() && !triggerBlacklist.isEmpty()) {
            throw new IllegalStateException(
                    "Either trigger whitelist or trigger blacklist may be specified, but not both");
        }

        if (!objectWhitelist.isEmpty() && !objectBlacklist.isEmpty()) {
            throw new IllegalStateException(
                    "Either object whitelist or object blacklist may be specified, but not both");
        }
    }

    /**
     * Check if a trigger is restricted by a whitelist or a blacklist.
     */
    private boolean isTriggerRestricted(final Class<?> triggerClass) {
        if (!triggerWhitelist.isEmpty()) {
            return !triggerWhitelist.contains(triggerClass.getSimpleName());
        } else if (!triggerBlacklist.isEmpty()) {
            return triggerBlacklist.contains(triggerClass.getSimpleName());
        } else {
            return false;
        }
    }

    /**
     * Check if an object is restricted by a whitelist or a blacklist.
     */
    private boolean isObjectRestricted(final Class<?> objectClass) {
        if (!objectWhitelist.isEmpty()) {
            return !objectWhitelist.contains(objectClass.getSimpleName());
        } else if (!objectBlacklist.isEmpty()) {
            return objectBlacklist.contains(objectClass.getSimpleName());
        } else {
            return false;
        }
    }

    /**
     * Register a dispatcher.
     *
     * @param owner
     * 		the object or the class of the object that "owns" the dispatcher.  It is safe to pass "this"
     * 		in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the trigger class of the dispatch
     * @param comment
     * 		an optional comment used to enhance the flowchart
     */
    public void registerDispatcher(final Object owner, final Class<?> triggerClass, final String comment) {

        registerTriggerLinkage(owner, triggerClass, comment, dispatcherMap);
    }

    /**
     * Register a dispatch observer.
     *
     * @param owner
     * 		the object or the class of the object that "owns" the observer. It is safe to pass "this"
     * 		in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the trigger class of the dispatch
     * @param comment
     * 		an optional comment used to enhance the flowchart
     */
    public void registerObserver(final Object owner, final Class<?> triggerClass, final String comment) {

        registerTriggerLinkage(owner, triggerClass, comment, observerMap);
    }

    /**
     * Register a linkage between an observer/dispatcher and a trigger.
     *
     * @param owner
     * 		the object or the class of the object that "owns" the observer or the dispatcher.
     * 		It is safe to pass "this" in a constructor for this parameter, as only the class if the object is used.
     * @param triggerClass
     * 		the trigger class
     * @param comment
     * 		an optional comment on the linkage
     * @param map
     * 		a map containing linkages for observers or dispatchers
     */
    private void registerTriggerLinkage(
            final Object owner,
            final Class<?> triggerClass,
            final String comment,
            final Map<Class<?>, Set<CommentedTrigger>> map) {

        throwArgNull(owner, "owner");

        final Class<?> ownerClass;
        if (owner instanceof final Class<?> cls) {
            ownerClass = cls;
        } else {
            ownerClass = owner.getClass();
        }

        if (isObjectRestricted(ownerClass) || isTriggerRestricted(triggerClass)) {
            return;
        }

        uniqueObjects.add(ownerClass);
        uniqueTriggers.add(triggerClass);

        final Set<CommentedTrigger> triggersForOwner = map.computeIfAbsent(ownerClass, k -> new HashSet<>());

        triggersForOwner.add(new CommentedTrigger(triggerClass, comment));
    }

    /**
     * Draw an object (either an observer or a dispatcher, or both).
     *
     * @param sb
     * 		a string builder where the mermaid file is being assembled
     * @param objectClass
     * 		the class of the object
     */
    private static void drawObject(final StringBuilder sb, final Class<?> objectClass) {
        sb.append(INDENTATION)
                .append(COMMENT)
                .append(" ")
                .append(objectClass.getName())
                .append("\n");
        sb.append(INDENTATION).append(objectClass.getSimpleName()).append("\n");
        sb.append(INDENTATION)
                .append("style ")
                .append(objectClass.getSimpleName())
                .append(" fill:#362,stroke:#000,stroke-width:2px,color:#fff\n");
    }

    /**
     * Draw a trigger.
     *
     * @param sb
     * 		a string builder where the mermaid file is being assembled
     * @param triggerClass
     * 		the class of the trigger
     */
    private static void drawTrigger(final StringBuilder sb, final Class<?> triggerClass) {
        final String name = triggerClass.getSimpleName();
        final String fullName = triggerClass.getName();

        sb.append(INDENTATION).append(COMMENT).append(" ").append(fullName).append("\n");
        sb.append(INDENTATION).append(name).append("{{").append(name).append("}}\n");
        sb.append(INDENTATION)
                .append("style ")
                .append(name)
                .append(" fill:#36a,stroke:#000,stroke-width:2px,color:#fff\n");
    }

    /**
     * Draw an arrow from a dispatcher to a trigger.
     *
     * @param sb
     * 		a string builder where the mermaid file is being assembled
     * @param dispatchClass
     * 		the dispatching class
     * @param trigger
     * 		the trigger that is being dispatched
     */
    private static void drawDispatchArrow(
            final StringBuilder sb, final Class<?> dispatchClass, final CommentedTrigger trigger) {

        sb.append(INDENTATION).append(dispatchClass.getSimpleName());

        final String comment = trigger.comment();
        if (trigger.comment() == null || trigger.comment().equals("")) {
            sb.append(" --> ");
        } else {
            validateComment(dispatchClass, comment);
            sb.append(" -- \"").append(comment).append("\" --> ");
        }

        sb.append(trigger.trigger().getSimpleName()).append("\n");
    }

    /**
     * Draw an arrow from a trigger to an observer.
     *
     * @param sb
     * 		a string builder where the mermaid file is being assembled
     * @param observerClass
     * 		the class observing the trigger
     * @param trigger
     * 		the trigger being observed
     */
    private static void drawObserverArrow(
            final StringBuilder sb, final Class<?> observerClass, final CommentedTrigger trigger) {

        sb.append(INDENTATION).append(trigger.trigger().getSimpleName());

        final String comment = trigger.comment();
        if (comment == null || comment.equals("")) {
            sb.append(" -.-> ");
        } else {
            validateComment(observerClass, comment);
            sb.append(" -. \"").append(comment).append("\" .-> ");
        }

        sb.append(observerClass.getSimpleName()).append("\n");
    }

    private static void validateComment(final Class<?> clazz, final String comment) {
        if (comment.contains("\"")) {
            throw new IllegalArgumentException(
                    "Dispatcher comments for class " + clazz + " contain illegal \" character(s).");
        }
    }

    /**
     * Build a mermaid flowchart.
     *
     * @return a string containing a flowchart in mermaid format
     */
    public String buildFlowchart() {
        final StringBuilder sb = new StringBuilder();

        sb.append("flowchart TD\n");

        sb.append("\n").append(INDENTATION).append(COMMENT).append(" observing and dispatching objects\n");
        uniqueObjects.stream()
                .sorted(Comparator.comparing(Class::getSimpleName))
                .forEachOrdered(object -> drawObject(sb, object));

        sb.append("\n").append(INDENTATION).append(COMMENT).append(" triggers\n");
        uniqueTriggers.stream()
                .sorted(Comparator.comparing(Class::getSimpleName))
                .forEachOrdered(object -> drawTrigger(sb, object));

        sb.append("\n").append(INDENTATION).append(COMMENT).append(" links from dispatchers to triggers\n");
        dispatcherMap.keySet().stream()
                .sorted(Comparator.comparing(Class::getSimpleName))
                .forEach(dispatcher -> dispatcherMap.get(dispatcher).stream()
                        .sorted(Comparator.comparing(a -> a.trigger().getSimpleName()))
                        .forEach(trigger -> drawDispatchArrow(sb, dispatcher, trigger)));

        sb.append("\n").append(INDENTATION).append(COMMENT).append(" links from triggers to observers\n");
        observerMap.keySet().stream()
                .sorted(Comparator.comparing(Class::getSimpleName))
                .forEach(observer -> observerMap.get(observer).stream()
                        .sorted(Comparator.comparing(a -> a.trigger().getSimpleName()))
                        .forEach(trigger -> drawObserverArrow(sb, observer, trigger)));

        return sb.toString();
    }

    /**
     * Write a mermaid flowchart to a file.
     *
     * @param file
     * 		the location of the file
     */
    public void writeFlowchart(final Path file) throws IOException {
        Files.writeString(file, buildFlowchart());
    }
}
