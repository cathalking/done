#!/bin/bash
lein clean && lein ring uberwar && rm -rf gae && unzip -d gae target/*.war && cp appengine-web.xml cron.xml gae/WEB-INF && $APPENGINE_HOME/bin/appcfg.sh update gae
