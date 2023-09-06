# Base image with Ubuntu 22.04
FROM ubuntu:22.04

# Set environment variables for Java and Gradle versions
ENV JAVA_VERSION=17
ENV GRADLE_VERSION=7.5.1

# Install necessary packages
RUN apt-get update && \
    apt-get install -y curl wget unzip

# Install Java
RUN apt-get install -y openjdk-${JAVA_VERSION}-jdk

# Install Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip -d /opt gradle-${GRADLE_VERSION}-bin.zip && \
    rm gradle-${GRADLE_VERSION}-bin.zip

# Set environment variables for Java and Gradle
ENV JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}-openjdk-amd64
ENV GRADLE_HOME=/opt/gradle-${GRADLE_VERSION}
ENV PATH=${GRADLE_HOME}/bin:${PATH}

# Verify Java and Gradle installations
RUN java -version && \
    gradle -v

# Set the working directory
WORKDIR /prwatech

COPY . /prwatech

RUN gradle clean

RUN chmod 777 ./gradlew

RUN ./gradlew :spotlessApply

RUN gradle build

EXPOSE 9090

CMD ["/bin/bash", "-c", "gradle bootRun > /dev/null 2>&1 & exec sleep infinity"]