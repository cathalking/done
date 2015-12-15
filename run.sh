#!/bin/bash
lein do clean, ring uberwar && rm -rf gae && unzip -d gae target/*.war && cp appengine-web.local.xml gae/WEB-INF/appengine-web.xml && cp cron.xml gae/WEB-INF && $APPENGINE_HOME/bin/dev_appserver.sh gae
