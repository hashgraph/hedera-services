package com.swirlds.config.api;

/**
 * Default value markers for special cases
 */
public sealed interface DefaultMarker
        permits DefaultMarker.EmptyList,
        DefaultMarker.EmptySet,
        DefaultMarker.EmptyMap,
        DefaultMarker.Null,
        DefaultMarker.Undefined {

    final class EmptyList implements DefaultMarker {
        private EmptyList() {}
        public static final EmptyList INSTANCE = new EmptyList();
    }

    final class EmptySet implements DefaultMarker {
        private EmptySet() {}
        public static final EmptySet INSTANCE = new EmptySet();
    }

    final class EmptyMap implements DefaultMarker {
        private EmptyMap() {}
        public static final EmptyMap INSTANCE = new EmptyMap();
    }

    final class Null implements DefaultMarker {
        private Null() {}
        public static final Null INSTANCE = new Null();
    }

    final class Undefined implements DefaultMarker {
        private Undefined() {}
        public static final Undefined INSTANCE = new Undefined();
    }

}