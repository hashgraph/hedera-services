module com.swirlds.config.processor {
    provides javax.annotation.processing.Processor with
            com.swirlds.config.processor.ConfigDataAnnotationProcessor;

    requires transitive com.swirlds.config.api;
    requires com.squareup.javapoet;
    requires java.compiler;
    requires org.antlr.antlr4.runtime;
    requires static com.google.auto.service;
    requires static com.github.spotbugs.annotations;
}
