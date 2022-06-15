FROM ubuntu:18.04

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y openssh-client haveged tar gzip git ca-certificates wget zip unzip sudo tcptraceroute

RUN cd /tmp && \
    wget --quiet https://jvm-storage.s3.amazonaws.com/openjdk-12.0.2_linux-x64_bin.tar.gz && \
    mkdir -p /usr/local/java && \
    tar -zxf openjdk-12.0.2_linux-x64_bin.tar.gz -C /usr/local/java && \
    update-alternatives --install "/usr/bin/java" "java" "/usr/local/java/jdk-12.0.2/bin/java" 1500 && \
    update-alternatives --install "/usr/bin/javac" "javac" "/usr/local/java/jdk-12.0.2/bin/javac" 1500 && \
    update-alternatives --install "/usr/bin/javadoc" "javadoc" "/usr/local/java/jdk-12.0.2/bin/javadoc" 1500 && \
    update-alternatives --install "/usr/bin/keytool" "keytool" "/usr/local/java/jdk-12.0.2/bin/keytool" 1500 && \
    rm -f /tmp/jdk-12.0.2_linux-x64_bin.tar.gz

RUN cd /tmp && \
    wget --quiet https://swirlds-docker-artifacts.s3.amazonaws.com/maven/apache-maven-3.6.1-bin.tar.gz && \
    tar -zxf apache-maven-3.6.1-bin.tar.gz && \
    mv apache-maven-3.6.1 /usr/local/maven && \
    rm -f /tmp/apache-maven-3.6.1-bin.tar.gz && \
    echo 'export M2_HOME="/usr/local/maven"' > /etc/profile.d/mvn.sh && \
    echo 'export JAVA_HOME="/usr/local/java/jdk-12.0.2"' > /etc/profile.d/java.sh && \
    update-alternatives --install "/usr/bin/mvn" "mvn" "/usr/local/maven/bin/mvn" 1500

RUN apt update \
    && apt install -y --no-install-recommends \
        make \
        gradle \
        python3-pip \
        ansible \
        postgresql-client-10 \
        git \
    && apt autoremove -y \
    && apt purge -y --auto-remove openjdk-* \
    && rm -rf /var/lib/apt/lists/ \
    && pip3 install setuptools \
    && pip3 install awscli

RUN wget -nv https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-3.2.0.1227-linux.zip \
    && unzip sonar-scanner-cli-3.2.0.1227-linux.zip \
    && mv sonar-scanner-3.2.0.1227-linux sonar-scanner \
    && rm -f sonar-scanner-cli-3.2.0.1227-linux.zip 

RUN wget -nv https://releases.hashicorp.com/terraform/0.11.10/terraform_0.11.10_linux_amd64.zip \
    && unzip terraform_0.11.10_linux_amd64.zip \
    && chmod +x terraform \
    && mv terraform /usr/local/bin/ \
    && rm terraform_0.11.10_linux_amd64.zip

COPY . /usr/bin
