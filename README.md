GitHub Deploy Maven Plugin
==========================

This plugin will augment the normal Maven artifact deployment process by also
deploying the artifacts to an associated GitHub project as file downloads.

With a variety of configuration options you can control which artifacts will be
deployed, whether or not they'll replace existing downloads of the same name,
or an alternate GitHub project the artifacts should be deployed to.



Usage
=====

The base configuration for the plugin will deploy all attached artifacts
without replacing any existing downloads of the same name.

    <plugin>
        <groupId>com.jakewharton</groupId>
        <artifactId>github-deploy-maven-plugin</artifactId>
        <version>1.0.0</version>
        <execution>
            <execution>
                <goals>
                    <goal>deploy</goal>
                </goal>
            </execution>
        </execution>
    </plugin>

There are a range of configuration options which can control the behavior of
the plugin. For a complete list, see the [documentation site][1].


Example
-------

`pom.xml` `<plugin>` configuration:

    <plugin>
        <groupId>com.jakewharton</groupId>
        <artifactId>github-deploy-maven-plugin</artifactId>
        <version>1.0.0</version>
        <configuration>
            <replaceExisting>true</replaceExisting>
            <ignoreTypes>
                <ignoreType>java-source</ignoreType>
            </ignoreTypes>
        </configuration>
        <executions>
            <execution>
                <goals>
                    <goal>deploy</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

`mvn deploy` partial output:

    [INFO] [github-deploy:deploy {execution: default}]
    [INFO] Assembling list of valid artifacts for deployment...
    [INFO] - Valid: github-deploy-maven-plugin-test-1.0.0-SNAPSHOT.jar (jar)
    [INFO] - Valid: github-deploy-maven-plugin-test-1.0.0-SNAPSHOT-javadoc.jar (javadoc)
    [INFO] - Ignore: github-deploy-maven-plugin-test-1.0.0-SNAPSHOT-sources.jar (java-source)
    [INFO] 
    [INFO] Assembling list of existing downloads...
    [INFO] - Delete: github-deploy-maven-plugin-test-1.0.0-SNAPSHOT.jar
    [INFO] 
    [INFO] Deploying "github-deploy-maven-plugin-test-1.0.0-SNAPSHOT.jar"...
    [INFO] - Sending artifact information and obtaining upload credentials...
    [INFO] - Uploading artifact to remote server...
    [INFO] 
    [INFO] Deploying "github-deploy-maven-plugin-test-1.0.0-SNAPSHOT-javadoc.jar"...
    [INFO] - Sending artifact information and obtaining upload credentials...
    [INFO] - Uploading artifact to remote server...
    [INFO] 
    [INFO] Successfully deployed 2 artifacts.


Documentation
-------------

 * Javadocs are available at [jakewharton.github.com/github-deploy-maven-plugin/][1].
 * Repository is hosted at [github.com/JakeWharton/github-deploy-maven-plugin/][2].



Developed By
============

* Jake Wharton - <jakewharton@gmail.com>


Contributors
------------

This plugin is based off of [tekkub][3]'s [github-upload][4] ruby script.



License
=======

    Copyright 2011 Jake Wharton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.




 [1]: http://jakewharton.github.com/github-deploy-maven-plugin/
 [2]: https://github.com/JakeWharton/github-deploy-maven-plugin/
 [3]: https://github.com/tekkub/
 [4]: https://github.com/tekkub/github-upload/
