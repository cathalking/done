(defproject done "0.1.0-SNAPSHOT"
  :description "Dunnit - Done Tracker"
  :url "https://done-1041.appspot.com/done"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.6"]
                 [cheshire "5.3.1"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.9.26"]
                 [hiccup "1.0.5"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-codec "1.0.0"]
                 [ring-server "0.3.1"]
                 [clj-time "0.11.0"]
                 ]
  :plugins [[lein-ring "0.8.13"]
            [lein-environ "1.0.0"]]
  :ring {:handler done.handler/app :init done.handler/init :destroy done.handler/destroy}
  :aot :all
  :profiles { :production {:ring {:open-browser? false, :stacktraces? false, :auto-reload? false}}
              :dev        {:ring {:handler done.handler/app :init done.handler/init-local} 
                           :dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.2.1"]]}
             })

