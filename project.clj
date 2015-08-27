(defproject done "0.1.0-SNAPSHOT"
  :description "Dunnit - Done Tracker"
  :url "https://done-1041.appspot.com/done"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.6"]
                 [cheshire "5.3.1"]
                 [org.clojure/data.codec "0.1.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-json "0.3.1"]
                 [ring-server "0.3.1"]
                 [environ "1.0.0"]
                 [gmail-clj "0.6.2"]
                 ]
  :plugins [[lein-ring "0.8.13"]
            [lein-environ "1.0.0"]]
  :ring {:handler done.handler/app}
  :aot :all
  :profiles { :production {:ring {:open-browser? false, :stacktraces? false, :auto-reload? false}
                          :jvm-opts ["-Ddomain=done-1041.appspot.com" "-Dprotocol=https" ]}
              :dev        {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.2.1"]]
                          :jvm-opts ["-Ddomain=localhost:3000" "-Dprotocol=http" ]}
             })

