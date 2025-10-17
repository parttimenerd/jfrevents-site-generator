[![REUSE status](https://api.reuse.software/badge/github.com/SAP/sapmachine-jfrevents-site-generator)](https://api.reuse.software/info/github.com/SAP/sapmachine-jfrevents-site-generator)

JFR Events Site Generator
=========================

Uses the data from the [jfreventscollector](https://github.com/SAP/sapmachine-jfreventcollector)
project to generate a website with all the JFR events seen at [SapMachine](https://sapmachine.io/jfrevents).
Nightly releases can be found at [parttimenerd.github.io/jfrevents](https://parttimenerd.github.io/jfrevents)
these are generated automatically whenever I update the underlying data set and might therefore
a bit fresher then the SapMachine page.
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

Site generator options:

```
Usage: generator [-hV] [-p=<prefix>] [--goat-counter-url
                 [=<goatCounterUrls>...]]... <target>
Generates a site with specified parameters.
      <target>            The target directory.
      --goat-counter-url[=<goatCounterUrls>...]
                          GoatCounter is an open source web analytics platform.
                            This is the URL for GoatCounter.
  -h, --help              Show this help message and exit.
  -p, --prefix=<prefix>   The filename prefix.
  -V, --version           Print version information and exit.
```

Development
-----------
`WatchKt` builds the version 21 of the site (currently `index.html`) every time the resources change.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc.
via [GitHub issues](https://github.com/SAP/sapmachine-jfrevents-site-generator/issues).
Contribution and feedback are encouraged and always welcome.
For more information about how to contribute, the project structure,
as well as additional contribution information,
see our [Contribution Guidelines](CONTRIBUTING.md).

## Troubleshooting
Builds might take longer on newer maven versions due to blocking
of http resources (and I don't know which).
Maven 3.6.3 seems to work fine.

## Security / Disclosure
If you find any bug that may be a security problem, please follow our instructions at
[in our security policy](https://github.com/SAP/sapmachine-jfrevents-site-generator/security/policy) on how to report it.
Please do not create GitHub issues for security-related doubts or problems.

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community
a harassment-free experience for everyone. By participating in this project,
you agree to abide by its [Code of Conduct](https://github.com/SAP/.github/blob/main/CODE_OF_CONDUCT.md) at all times.

TODO
----
- show all the types that an event depends on


License
-------
Copyright 2023 - 2025  SAP SE or an SAP affiliate company and contributors.
Please see our LICENSE for copyright and license information.
Detailed information including third-party components and their
licensing/copyright information is available via the REUSE tool.