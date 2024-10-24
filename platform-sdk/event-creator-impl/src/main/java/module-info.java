module org.hiero.event.creator.impl {
    requires transitive org.hiero.event.creator;

    provides org.hiero.event.creator.EventCreator with
            org.hiero.event.creator.impl.EventCreatorImpl;
}
