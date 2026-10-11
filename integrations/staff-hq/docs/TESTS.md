# Test execution

Java: use JDK 25 and `mvn -f source/connector/pom.xml verify`; Dragon builds in its own repository. The EC 7 native linkage check needs the installed EC/nightcore JARs on the test classpath; local validation supplied the exact version. It skips only that optional linkage check if those JARs are absent.

History: `python3 tests/history_tests.py`. PHP syntax: `rg --files source/web -g '*.php' | xargs -n1 php -l`.

The full `live_tests.py` harness starts its own isolated MariaDB/PHP servers and synthetic users/API client. It needs the local runtime binaries/extensions, php-args.json, socketshim and Chromium described in its header. Do not point this fixture at a production database. Browser fixtures contain synthetic staff/player records only and are not deployed.
