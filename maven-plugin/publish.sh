#!/bin/bash

cd baratine-maven-plugin
mvn -e -P publish clean deploy

cd ../baratine-maven-archetype
mvn -e -P publish clean deploy
