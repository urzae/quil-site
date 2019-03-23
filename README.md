# Quil Website

Source code of [http://quil.info](http://quil.info).

## Prerequisites

Some additional libraries are required to run in headless mode.
On Ubuntu, these are the things you need to install first:
```shell
sudo apt-get install xvfb libxrender1 libxtst6 libxi6
```

[More information on this](https://github.com/processing/processing/wiki/Running-without-a-Display)

## Usage

```shell
lein cljsbuild once
xvfb-run lein run -m quil-site.core/run 8080
```
And open [http://localhost:8080](http://localhost:8080).

## License

Copyright © 2018 Quil

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
