FROM ubuntu:18.04

# Install openssl as per https://hub.docker.com/r/shamelesscookie/openssl/dockerfile
ENV \
  VERSION=1.0.2u \
  SHA256=ecd0c6ffb493dd06707d38b14bb4d8c2288bb7033735606569d8f90f89669d16
RUN \
  apt update && apt upgrade -y && apt install -y build-essential checkinstall zlib1g-dev curl && \
  cd /usr/local/src/ && \
  curl https://www.openssl.org/source/openssl-${VERSION}.tar.gz -o openssl-${VERSION}.tar.gz && \
  sha256sum openssl-${VERSION}.tar.gz | grep ${SHA256} && \
  tar -xf openssl-${VERSION}.tar.gz && \
  cd /usr/local/src/openssl-${VERSION} && \
  ./config --prefix=/usr/local/ssl --openssldir=/usr/local/ssl shared zlib && \
  make && \
  make test && \
  make install && \
  cd /etc/ld.so.conf.d/ && \
  echo "/usr/local/ssl/lib" > openssl-${VERSION}.conf && \
  ldconfig -v && \
  rm /usr/bin/c_rehash && \
  rm /usr/bin/openssl && \
  rm /usr/local/src/openssl-${VERSION}.tar.gz && \
  rm -rf /usr/local/src/openssl-${VERSION} && \
  apt remove -y build-essential checkinstall zlib1g-dev curl && \
  apt-get autoremove -y && \
  apt-get clean && \
  rm -rf /var/lib/apt/lists/*
ENV \
  PATH=/usr/local/ssl/bin:$PATH \
  SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt \
  SSL_CERT_DIR=/etc/ssl/certs
