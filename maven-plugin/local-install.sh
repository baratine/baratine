#!/bin/bash

VERSION=`cat VERSION`
cd baratine-maven-archetype
mvn -DVERSION=$VERSION -e clean install

#site

cd ../baratine-maven-plugin
mvn -DVERSION=$VERSION -e clean install
#site
