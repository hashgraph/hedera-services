module com.swirlds.config.processor {

    provides javax.annotation.processing.Processor with
            com.swirlds.config.processor.ConfigDataAnnotationProcessor;

    requires static com.google.auto.service.annotations;
    requires java.compiler;
    requires com.swirlds.config;
    requires org.jboss.forge.roaster.api;
}