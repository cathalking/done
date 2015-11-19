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
                 ;[com.google.appengine/appengine-testing "1.9.26"]
                 ;[com.google.appengine/appengine-api-stubs "1.9.26"]
                 [com.google.http-client/google-http-client "1.20.0"] 
                 [hiccup "1.0.5"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-codec "1.0.0"]
                 [ring-server "0.3.1"]
                 [clj-time "0.11.0"]
                 [crypto-password "0.1.3"]
                 [com.google.apis/google-api-services-datastore-protobuf "v1beta2-rev1-3.0.2"]
                 ]
  :plugins [[lein-ring "0.8.13"]
            [lein-environ "1.0.0"]]
  :ring {:handler done.handler/app :init done.handler/init :destroy done.handler/destroy :web-xml "web.xml"}
  :aot :all
  :profiles { :production {:ring {:open-browser? false, :stacktraces? false, :auto-reload? false}}
              :dev        {:ring {:handler done.handler/app :init done.handler/init-local} 
                           :dependencies [ [ring-mock "0.1.5"] 
                                           [ring/ring-devel "1.2.1"]
                                           ;[com.google.appengine/appengine-testing "1.9.20"]
                                           ;[com.google.appengine/appengine-api-stubs "1.9.20"]
                                          ]
                           ;:plugins [[lein-extend-cp "0.1.0"]]
                           ;:lein-extend-cp {:paths ["/Users/cathalking/programming/appengine-java-sdk-1.9.25/lib/impl/appengine-local-runtime.jar", 
                           ;                         "/Users/cathalking/programming/appengine-java-sdk-1.9.25/lib/impl/appengine-api-stubs.jar",
                           ;                         "/Users/cathalking/programming/appengine-java-sdk-1.9.25/lib/impl/appengine-api.jar" ] }
                          }
             })

