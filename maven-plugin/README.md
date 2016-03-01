Maven plugin for generating and building Baratine projects.

## Install 

```
  <project>
    ...
    <build>
      
      <plugins>
        <plugin>
          <groupId>io.baratine</groupId>
          <artifactId>baratine-maven-plugin/artifactId>
        </plugin>
      </plugins>
      
    </build>
    
  </project>
```

## Generating Baratine Project

A Baratine project can be generated using the following Maven command:

`mvn archetype:generate -DarchetypeGroupId=io.baratine -DarchetypeArtifactId=baratine-maven-archetype -DgroupId=org.acme -DartifactId=MyApp -DarchetypeVersion=0.10.2 -DinteractiveMode=false`

Note: change org.acme and MyApp to desired groupId and artifactId.

## Building Baratine Project

Baratine requires special packaging which, similarly to a .war, adopts a specific 
directory structure. 

baratine-maven-plugin provides support for building baratine deployment files (.bar) 
via defining support for maven baratine packaging.

For default Maven pom.xml for Baratine see [pom.xml] (https://github.com/baratine/maven-collection-baratine/blob/master/baratine-archetype/src/main/resources/archetype-resources/pom.xml)
 
