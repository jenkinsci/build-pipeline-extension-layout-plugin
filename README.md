[Build Pipeline Plugin] Extension Layout supporting build-flow jobs

=====================

Building the Project
--------------------

### Dependencies
* [Apache Maven][maven] 3.0.4 or later
* build-pipeline-plugin 1.5.1
* build-flow-plugin 0.18
* buildgraph-view 1.1.1
* [graphviz]

### Targets
```shell
  $ mvn clean install
  $ mvn clean install -DskipTests -Dcheckstyle.skip -Dfindbugs.skip
  $ mvn hpi:run
  $ mvnDebug hpi:run
```

Installing Plugin Locally
-------------------------
1. Install [graphviz] on Linux or Windows
2. Build the project to produce `target/build-pipeline-plugin-extension-layout.hpi`
3. Remove any installation of the build-pipeline-plugin-extension-layout in `$user.home/.jenkins/plugins/`
4. Copy `target/build-pipeline-plugin-extension-layout.hpi` to `$user.home/.jenkins/plugins/`
5. Start/Restart Jenkins

[Build Pipeline Plugin]: https://wiki.jenkins-ci.org/display/JENKINS/Build+Pipeline+Plugin
[maven]: https://maven.apache.org/
[graphviz]: http://www.graphviz.org/
