(ns done.repl
  (:use done.handler
        done.dunnit
        done.gmailauth
        ring.server.standalone
        [ring.middleware file-info file]
        ))

(defonce server (atom nil))

(defn get-handler []
  ;; #'app expands to (var app) so that when we reload our code,
  ;; the server is forced to re-resolve the symbol in the var
  ;; rather than having its own copy. When the root binding
  ;; changes, the server picks it up without having to restart.
  (-> #'app
      ; Makes static assets in $PROJECT_DIR/resources/public/ available.
      (wrap-file "resources")
      ; Content-Type, Content-Length, and Last Modified headers for files in body
      (wrap-file-info)))

(defn start-server
  "used for starting the server in development mode from REPL"
  [& [port]]
  (let [port (if port (Integer/parseInt port) 3000)]
    (reset! server
            (serve (get-handler)
                   {:port port
                    ;:init ("Dunnit is starting up"))
                    :open-browser? false
                    :auto-reload? true
                    ;:destroy (println "Dunnit is shutting down")
                    :join true}))
    (load-sys-props "/var/tmp/creds2.json")
    ))

(defn stop-server []
  (.stop @server)
  (reset! server nil))
