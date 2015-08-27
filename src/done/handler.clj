(ns done.handler
  (:require [compojure.core :refer :all]
            [done.dunnit :as dunnit]
            [done.gmailauth :as gmailauth]
            [done.views :as views]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.string :as str]
            [cheshire.core :as json]
            [gmail-clj.core :as gm]
            [ring.util.response :as r]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            ))

(defroutes app-routes
  (GET "/" [] (r/redirect "/dunnit"))

  (GET "/dunnit" []
    (views/done-home-page))

  (POST "/dunnit" [done]
    (if (not (clojure.string/blank? done))
      (dunnit/persist-in dunnit/dones done))
    (r/redirect-after-post "/dunnit"))

  (POST "/done" {body :body}
    (let [data  (get-in body [:message :data])
         pub-sub-message body
         gmail-notif (json/parse-string (dunnit/decode-msg data) true)
         gmail-notif-json-str (dunnit/decode-msg data)]
     ;(println "Received: " (:message pub-sub-message))
     (if (not (empty? gmail-notif))
       (->
        (r/response 
          (do
            (dunnit/persist-in dunnit/notifications {:gmail-notif gmail-notif :history-api-resp (dunnit/history (:historyId gmail-notif))})
            (dunnit/persist-in dunnit/pub-sub-messages {:gmail-notif gmail-notif, :raw-message (:message pub-sub-message)})
            (dunnit/process-latest-dunnit-emails)
        ) )
        (r/status 200)
        (r/header "Content-Type" "application/json"))
       (->
         (r/response "Message must be non-empty")
         (r/status 400)
         (r/header "Content-Type" "application/json"))
     )
   ))

  ;(GET "/history/:historyid" [historyid]
  ;  (r/response
  ;    (dunnit/history historyid)
  ;     ))

  ;(GET "/listdunnits/new" []
  ;  (r/response
  ;    (dunnit/list-all-dunnits dunnit/label-dunnit-new)
  ;    ))

  ;(GET "/listdunnits/processed" []
  ;  (r/response
  ;    (dunnit/list-all-dunnits dunnit/label-dunnit-processed)
  ;    ))

  ;(GET "/dunnitssummary" []
  ;  (r/response
  ;    (dunnit/dunnits-summary)
  ;    ))

  ;(GET "/message/:messageid" [messageid]
  ;  (r/response
  ;    (dunnit/get-message messageid)
  ;    ))

  ;(GET "/messagecontent/:messageid" [messageid]
  ;  (r/response
  ;    (dunnit/extract-message-content (dunnit/get-message messageid))
  ;    ))

  ;(GET "/trash/:messageid" [messageid]
  ;  (r/response
  ;    (dunnit/get-message messageid)
  ;    ))

  ;(GET "/allmessages" []
  ;  (r/response @dunnit/pub-sub-messages))

  ;(GET "/apitester" []
  ;  (views/api-tester-page))

  ;(POST "/apitester" [http-method api-url]
  ;  (if (= "GET" http-method)
  ;       (r/response (dunnit/gae-get-req api-url))
  ;     (r/response (str "Received " http-method " " api-url))))

  ;(POST "/done2" {body :body}
  ;;   (r/response (dunnit/process-latest-dunnit-emails))
  ;    ;(r/response (dunnit/get-latest-dunnit-messages))
  ;    )

  ;(POST "/processdunnit" [messageid]
  ;  (let [message-content (dunnit/extract-message-content (dunnit/get-message messageid))]
  ;    (dunnit/process-dunnit messageid)
  ;    (println message-content)
  ;    (r/redirect-after-post "/dunnit")
  ;    )
  ;  )

  ;(POST "/ackdone" {body :body}
  ;  (println "Ack Received for: " body)
  ;  (->
  ;    (r/response "")
  ;    (r/status 200)
  ;    (r/header "Content-Type" "application/json"))
  ; )
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

