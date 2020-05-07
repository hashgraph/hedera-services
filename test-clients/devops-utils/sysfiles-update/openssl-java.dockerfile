FROM openssl:latest

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y wget vim
RUN cd /tmp && \
    wget --quiet https://jvm-storage.s3.amazonaws.com/openjdk-12.0.2_linux-x64_bin.tar.gz && \
    mkdir -p /usr/local/java && \
    tar -zxf openjdk-12.0.2_linux-x64_bin.tar.gz -C /usr/local/java && \
    update-alternatives --install "/usr/bin/java" "java" "/usr/local/java/jdk-12.0.2/bin/java" 1500 && \
    update-alternatives --install "/usr/bin/javac" "javac" "/usr/local/java/jdk-12.0.2/bin/javac" 1500 && \
    update-alternatives --install "/usr/bin/javadoc" "javadoc" "/usr/local/java/jdk-12.0.2/bin/javadoc" 1500 && \
    rm -f /tmp/jdk-12.0.2_linux-x64_bin.tar.gz
