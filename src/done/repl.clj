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
                    :init (println "gae-app-demo is starting up")
                    :open-browser? false
                    :auto-reload? true
                    :destroy (println "gae-app-demo is shutting down")
                    :join true}))
    (load-creds "/var/tmp/creds2.json")
    (println (str "You can view the site at " (app-path "")))))

(defn stop-server []
  (.stop @server)
  (reset! server nil))
