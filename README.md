## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

Or from repl:

    (use 'done.repl)
    (start-server)

## Deploying to Google App Engine

- Add your valid oauth + app-engine project details to appengine-web.xml (see appengine-web.dummy.xml)
- From project base run:
	lein clean && lein ring uberwar && rm -rf gae && unzip -d gae target/*.war && cp appengine-web.xml gae/WEB-INF && $APPENGINE_HOME/bin/appcfg.sh update gae
