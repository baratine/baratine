#!/bin/bash

#export MAVEN_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005

cd baratine-maven-plugin/src/it/T0004

mvn -e -X clean package
