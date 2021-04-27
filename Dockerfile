FROM ubuntu:20.04

RUN apt-get update --fix-missing && \
    apt-get install -y \
     openjdk-11-jre-headless