(ns done.handler
  (:require [compojure.core :refer :all]
            [done.dunnit :as dunnit]
            [done.online :refer [sys-props-file]]
            ;[done.offline :refer [sys-props-file]]
            [done.gmailauth :as gmailauth]
            [done.views :as views]
            [done.ringdebug :as rd]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clj-time.core :as t]
            [ring.util.response :as r]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            ))

(def tables (ref {}))

(defroutes app-routes
  (GET "/" [] (r/redirect "/dunnit"))

  (GET "/login" [redirect-to] 
    (views/login-page (if (nil? redirect-to) "/dunnit" redirect-to)))

  (GET "/logout" [] 
    {:status 302
     :session nil
     :headers {"Location" "/login"}})

  (POST "/login" {:keys [session params]}
    {:status 302
     :session (assoc session :username (:username params))
     :headers {"Location" (if (nil? (:redirect-to params)) "/dunnit" (:redirect-to params))}})

  (GET "/dunnit" []
      (views/done-home-page :dones @dunnit/dones))

  (POST "/dunnit" {:keys [session params]}
    (if (not (clojure.string/blank? (:done params)))
      (dunnit/persist-in dunnit/dones {:done (:done params) :message-id "N/A" :date (t/now) :from (:username session) :client "Dunnit Website"}))
    (r/redirect-after-post "/dunnit"))

  (GET "/customtables" []
      (views/custom-table-creator :dones @dunnit/dones :tables @tables))

  (POST "/customtables" {form-params :form-params}
    (let [cols (-> (dissoc form-params "title")
                   (dissoc "grouping")
                   clojure.walk/keywordize-keys
                   keys)
          table-title (get form-params "title")
          grouping (get form-params "grouping")]    
      (do
        (dosync 
          (alter tables assoc 
                 table-title
                 {:cols cols :grouping grouping}
                      ))
          (r/redirect-after-post "/customtables")
        )))

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
            ;(dunnit/persist-in dunnit/notifications {:gmail-notif gmail-notif :history-api-resp (online/history (:historyId gmail-notif) (dunnit/label-dunnit-new))})
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

(defn wrap-simple-auth [handler]
  (fn [req]
    (let [session (:session req)
          username (:username session)
          uri (:uri req)]
      (do 
        (cond 
              (= uri "/login") 
                (handler req)
              (= uri "/done") 
                (handler req)
              (nil? username) 
                {:status 302 :headers {"Location" (str "/login" "?redirect-to=" uri)}}
        :else (handler req))
        )
      )
    )
  )

(def app
  (-> (routes app-routes standard-routes)
    (wrap-json-body {:keywords? true})
    (wrap-json-response)
    (wrap-simple-auth)
    ;(rd/wrap-spy "what the auth handler sees")
    (handler/site)
    ;(rd/wrap-spy "what the site handler sees")
    )
  )

(defn init [] (dunnit/process-previous-dunnit-emails true))

(defn init-local [] 
  (do 
      (dunnit/load-sys-props sys-props-file)
      ;(dunnit/load-sys-props "/var/tmp/creds.dunnitinbox.json")
      (dunnit/process-previous-dunnit-emails true)))
