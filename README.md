JFR Events Site Generator
=========================

Uses the data from the [jfreventscollector](https://github.com/parttimenerd/jfreventcollector)
project to generate a website with all the JFR events seen at [SapMachine](https://sapmachine.io/jfrevents).

![Screenshot](img/screenshot.png)

Build
-----
```shell
# update the dependencies
mvn dependency:resolve -U
mvn clean package assembly:single
# generate it into site
java -jar target/jfrevents-site-generator-full.jar site
```

Development
-----------
`WatchKt` builds the version 19 of the site (currently `index.html`) every time the resources change.

`bin/publish.sh` is used to publish the site at [sapmachine.io](https://sapmachine.io).

License
-------
GPLv2