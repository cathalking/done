(ns done.repl
  (:require 
            [done.handler :as handler]
            [ring.server.standalone]
            )
  (:use 
        [ring.middleware file-info file]))

(defonce server (atom nil))

(defn get-handler []
  ;; #'app expands to (var app) so that when we reload our code,
  ;; the server is forced to re-resolve the symbol in the var
  ;; rather than having its own copy. When the root binding
  ;; changes, the server picks it up without having to restart.
  (-> (var handler/app)
      ; Makes static assets in $PROJECT_DIR/resources/public/ available.
      (wrap-file "resources")
      ; Content-Type, Content-Length, and Last Modified headers for files in body
      (wrap-file-info)))

(defn ^:dynamic start-server
  "used for starting the server in development mode from REPL"
  [& [port]]
  (let [port (if port (Integer/parseInt port) 3000)]
    (reset! server
            (ring.server.standalone/serve (get-handler)
                   {:port port
                    :init handler/init-local
                    :stacktraces? true
                    :open-browser? false
                    :auto-reload? true
                    :destroy handler/destroy-local
                    :join true}))
    ))

(defn ^:dynamic stop-server []
  (.stop @server)
  (reset! server nil))
