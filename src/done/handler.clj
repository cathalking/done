(ns done.handler
  (:require [compojure.core :refer :all]
            [done.dunnit :as dunnit]
            [done.gmailauth :as gmailauth]
            [done.views :as views]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clj-time.core :as t]
            [ring.util.response :as r]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            ))

(defroutes app-routes
  (GET "/" [] (r/redirect "/dunnit"))

  (GET "/dunnit" []
      (views/done-home-page @dunnit/dones))

  (POST "/dunnit" [done]
    (if (not (clojure.string/blank? done))
      (dunnit/persist-in dunnit/dones {:done done :message-id "N/A" :date (t/now) :from "Logged in user" :client "Dunnit Website"}))
    (r/redirect-after-post "/dunnit"))

  (POST "/done" {body :body}
    (let [data  (get-in body [:message :data])
          pub-sub-message body
          gmail-notif (json/parse-string (dunnit/decode-msg data) true)
          gmail-notif-json-str (dunnit/decode-msg data)]
     (println "Received: " (:message pub-sub-message))
     (if (not (empty? gmail-notif))
       (->
        (r/response 
          (do
            (dunnit/persist-in dunnit/notifications {:gmail-notif gmail-notif :history-api-resp (dunnit/history (:historyId gmail-notif))})
            (dunnit/persist-in dunnit/pub-sub-messages {:gmail-notif gmail-notif, :raw-message (:message pub-sub-message)})
            (str "Processed " (count (dunnit/process-latest-dunnit-emails true)) " new dones")))
        (r/status 200)
        (r/header "Content-Type" "application/json"))
       (->
         (r/response "Message must be non-empty")
         (r/status 400)
         (r/header "Content-Type" "application/json"))
     )
    )
  )
)

(defroutes standard-routes
           (route/resources "/")
           (route/not-found "Not Found"))

(def app
  (-> (routes app-routes standard-routes)
    (wrap-json-body {:keywords? true})
    (wrap-json-response)
    (handler/site))
  )

(defn init [] (dunnit/process-previous-dunnit-emails true))

(defn init-local [] 
  (do (dunnit/load-sys-props "/var/tmp/creds2.json")
      (dunnit/process-previous-dunnit-emails true)))
