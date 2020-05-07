FROM openssl-java:latest

RUN mkdir -p /tools/scratch
RUN mkdir -p /tools/run
ADD run/ /tools/run
COPY aliases.sh /root/.bash_aliases

ENV \
  TOOLS_PATH=/tools \
  SCRATCH_PATH=/tools/scratch

WORKDIR /workspace

CMD ["/bin/sh", "-c", "bash"]
