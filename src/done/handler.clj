(ns done.handler
  (:require 
            [done.dunnit :as dunnit]
            [done.online :refer [sys-props-file]]
            ;[done.offline :refer [sys-props-file]]
            [done.googleoauth :as googleoauth]
            [done.views :as views]
            [done.ringdebug :as rd]
            ;[done.cache :as cache]
            [done.google-login :as google-login]
            [done.datastore :as gae]
            [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [blank?]]
            [compojure.core :refer [GET POST routes defroutes]]
            [compojure.route :as route]
            ;[compojure.handler :as handler]
            [ring.util.response :as r]
            [ring.middleware json session keyword-params flash multipart-params params nested-params]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            ))

(defn show-login-page [{:keys [flash]}] 
  {:status 200
   :body (views/login-page-google)
   :flash flash
  })

(defn logout-user [_] 
  {:status 302
   :session nil
   :headers {"Location" "/login"}
  })

(defn show-settings-page [{:keys [session flash]}]
  {:status 200, 
   :body (views/settings-page :errors (:errors flash))
  })

(defn show-registration-page [{:keys [session flash]}]
  {:status 200, 
   :body (views/registration-page :errors (:errors flash))
  })

(defn show-admin-page [{:keys [session flash]}]
  {:status 200, 
   :body (views/admin-page (dunnit/all-nudge-users))
  })

(def logout-user-after-macroexpand
  (compojure.core/make-route :get 
    (clout.core/route-compile "/logout") 
    #(compojure.core/let-request [_ %] {:status 302, :session nil, :headers {"Location" "/login"}})))

(defn show-dunnit-homepage [{:keys [session]}]
  {:status 200, 
   :body (views/done-home-page 
            :dones (dunnit/search-dones-by-user (:username session))
            :user-details (:user-details session)),
   :headers {"Content-Type" "text/html; charset=utf-8"}
  })

(defn process-submitted-done [{:keys [session params]}]
  (if (blank? (:done params)) 
    {:status 303, :headers {"Location" "/dunnit"}, :flash {:errors {:done "Please enter dunnit"}}}
    (do
      (dunnit/add {:kind "done", :done (:done params), :message-id "N/A", :date (tc/to-date (t/now)), :username (:username session), :client "Dunnit Website"})
      {:status 303, :headers {"Location" "/dunnit"}
      })
  ))

(defn show-dunnit-search [{:keys [session flash]}]
  {:status 200, 
   :body (views/done-search-page 
            ;:dones (:search-dones flash) 
            ;:user-email-results (:search-useremails flash) 
            ;:username-results (:search-usernames flash) 
            :results (:search-results flash)
            :user-details (:user-details session))
  })

(defn process-submitted-search [{:keys [session params]}]
  (if (blank? (:search-text params))
    {:status 303, :headers {"Location" "/dunnitsearch"}, :flash {:errors {:done "Please enter search text"}}}
    (let [results (dunnit/search (:search-text params) (:type params))]
      {:status 303, :headers {"Location" "/dunnitsearch"}, 
        :flash {
          ;:search-dones (dunnit/search-dones (:done-text params))
          ;:search-useremails (dunnit/search-users (:user-email params))
          ;:search-usernames (dunnit/search-usernames (:username params))
          :search-results results
          }})
  ))

(defn save-settings [{:keys [session params form-params]}]
  (let [username (:username session)
        existing-prefs (first (dunnit/search-prefs username))
        notif-days (->> (get form-params "days") (map #(Integer/parseInt %))) ; Take from form-params because params map only contains single-value
        notif-times (->> (get form-params "times") (map #(Integer/parseInt %)))
        disable-nudges (= "on" (:no-nudges params))
        new-prefs {:notif-times notif-times, :notif-days notif-days, 
               :date (tc/to-date (t/now)), :disable-nudges disable-nudges, :username username}]
    (cond 
      (and (or (empty? notif-days) 
                (empty? notif-times))
            (blank? (:no-nudges params)))
        {:status 303, :headers {"Location" "/settings"}, :flash {:errors {:nudge-times "Please select nudge settings"}}}
      (nil? existing-prefs) 
        (do
          (dunnit/add new-prefs)
          {:status 303, :headers {"Location" "/settings"}})
      :else
        (do
          (dunnit/update (:key existing-prefs) new-prefs)
          {:status 303, :headers {"Location" "/settings"}})
    )))

(defn username-taken? [username]
  (not (empty? (dunnit/search-usernames username))))

(defn register-user [{:keys [session params]}]
  (let [username (:username params)
        email (get-in session [:user-details :email])]
    (cond 
      (blank? username) 
        {:status 303, :headers {"Location" "/register"}, 
         :flash {:errors {:username "Enter a username ... crazy fool"}}}
      (username-taken? username)
        {:status 303, :headers {"Location" "/register"}, 
         :flash {:errors {:username "Great username... but it's taken"}}}
      :else 
        (do
          (dunnit/add {:kind "user", :username username, :email email, :date (tc/to-date (t/now))})
          {:status 303, :headers {"Location" "/settings"},
           :session (assoc session :username username)
          })
    )))

(defn add-email [{:keys [params]}]
  (if (blank? (:email params)) 
    {:status 303, :headers {"Location" "/settings"}, :flash {:errors {:email "Please enter email"}}}
    {:status 200, :body (views/add-email (:email params))}
  ))

(defn cron-send-nudges [_]
  (let [nudge-time (t/hour (t/now))]
    (println "Handle cron request to send nudges to registered users for time" nudge-time)
    (dunnit/send-nudges2 nudge-time)
    {:status 200}
  ))

(defn cron-ping [_] 
  (do
    (println "Ping")
    {:status 200}))

(defn admin-send-nudges [_] 
  (do
    (dunnit/send-nudges)
    {:status 303, :headers {"Location" "/admin"}}
  ))

(defn send-nudges [_] 
  (do
    (println "Actually sending nudge emails")
    (dunnit/send-nudges)
    {:status 200}
  ))

(def tables 
  (ref {}))

(defn show-customtables [_]
  {:status 200,
   :body (views/custom-table-creator :dones (dunnit/all-dones) :tables @tables)
  })

(defn create-customtable [{:keys [form-params]}]
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
               {:cols cols, :grouping grouping}
                    ))
        (r/redirect-after-post "/customtables")
    )
  ))

(defn process-done-inbox-notification [{body :body}]
  (let [data  (get-in body [:message :data])
        gmail-notif (json/parse-string (dunnit/decode-msg data) true)]
    (if (not (empty? gmail-notif))
      (do
        (dunnit/process-latest-dunnit-emails true)
        { :status 200
        })
      (->
        (r/response "Message must be non-empty")
        (r/status 400)
        (r/header "Content-Type" "application/json"))
    )
  ))

(defn show-authorization-request-page [{:keys [flash]}] 
  {:status 303,
   :headers {"Location" (google-login/request-user-to-authorize
                          :client_id (:client_id (googleoauth/oauth2-web-app-creds))
                          :original-uri (if (nil? (:redirect-to flash)) "/dunnit" (:redirect-to flash))
                          :scopes ["email" "profile"])}
  })

(defn authorization-callback [{:keys [query-params cookies session]}]
  (let [auth-code (get query-params "code")
        redirect-uri (get query-params "state")
        access-token-resp (google-login/request-access-token auth-code)
        user-details (:body (google-login/request-user-details (get-in access-token-resp [:body :access_token])))
        registered-user (first (dunnit/search-users (:email user-details)))]
    (if (nil? registered-user)
      {:status 303, :headers {"Location" "/register"},
                  :session (assoc session :user-details user-details)}
      {:status 303, :headers {"Location" redirect-uri},
                  :session (assoc session :username (:username registered-user)
                                          :user-details user-details)})
  ))

(defn show-user-details [{:keys [session]}]
  {:status 200, :body (:user-details session)
  })

(defn redirect-via [&{:keys [to via]}]
  (fn [req]
    {:status 303, :flash {:redirect-to to}, :headers {"Location" via}}
  ))

(defn redirect [uri]
  (fn [req]
    {:status 303, :headers {"Location" uri}}
  ))

(defn routing-handler [handler]
  (fn [req]
    (let 
      [matched-handler 
        (match req
          {:request-method _, :uri uri, :auth-needed? true} (redirect-via :via "/login" :to uri)
          {:request-method :get, :uri "/"} (redirect "/dunnit")
          {:request-method :post, :uri "/done"} process-done-inbox-notification
          {:request-method :get, :uri "/login"} show-login-page
          {:request-method :get, :uri "/logout"} logout-user
          {:request-method :get, :uri "/dunnit"} show-dunnit-homepage
          {:request-method :post, :uri "/dunnit"} process-submitted-done
          {:request-method :get, :uri "/dunnitsearch"} show-dunnit-search
          {:request-method :post, :uri "/dunnitsearch"} process-submitted-search
          {:request-method :get, :uri "/customtables"} show-customtables
          {:request-method :post, :uri "/customtables"} create-customtable
          {:request-method :get, :uri "/oauth2/google"} show-authorization-request-page
          {:request-method :get, :uri "/oauth2/callback"} authorization-callback
          {:request-method :get, :uri "/oauth2/userdetails"} show-user-details
          {:request-method :get, :uri "/settings"} show-settings-page
          {:request-method :post, :uri "/settings"} save-settings
          {:request-method :get, :uri "/admin"} show-admin-page
          {:request-method :post, :uri "/admin"} admin-send-nudges
          {:request-method :get, :uri "/register"} show-registration-page
          {:request-method :post, :uri "/register"} register-user
          {:request-method :post, :uri "/addemail"} add-email
          {:request-method :get, :uri "/cron/nudge"} cron-send-nudges
          {:request-method :get, :uri "/cron/wakeup"} cron-ping
          ;{:request-method :get, :uri "/stats"} show-memcache-stats
          :else handler)]
      (matched-handler req))
  ))

(def unauthenticated-routes
  #{"/oauth2/google"
    "/oauth2/callback"
    "/login"
    "/logout"
    "/done"
    "/cron/wakeup"
    "/cron/nudge"
  })

(defn wrap-authentication-needed? [handler]
  (fn [{:keys [session uri] :as req}]
    (let [auth-needed? (cond (unauthenticated-routes uri) false
                             (nil? (:user-details session)) true
                        :else false)]
      (handler (assoc req :auth-needed? auth-needed?)))
  ))

(comment 
  (defn environment-decorator
    "decorates the given application with a local version of the app engine environment"
    [app]
      (fn [request]
        (gae/with-app-engine (gae/login-aware-proxy request)
        (app request))))
)

(def app
  (->
    ;(routes app-routes standard-routes)
    ;(routing-handler (environment-decorator (route/not-found "Not Found")))
    (routing-handler (route/not-found "Not Found"))
    ;(rd/wrap-spy "what the 'routing-handler' sees")
    ;(wrap-auth-redirect)
    (wrap-authentication-needed?)
    (wrap-json-body {:keywords? true})
    (wrap-json-response)
    (wrap-keyword-params)
    (wrap-nested-params)
    (wrap-params)
    (wrap-multipart-params)
    (wrap-flash)
    (wrap-session)
    )
  )

(defn setup-test-data []
  (if (= "true" (System/getProperty "load-test-data"))
    (do
      (println "Adding test data")
      (dunnit/add {:kind "user", :username "cathalking", :email "cathalking1@gmail.com", :date (tc/to-date (t/now))})
      (dunnit/add {:kind "preferences", :notif-times [12, 15, 18], :notif-days [1,2,3,4,5,6,7], 
                   :date (tc/to-date (t/now)), :disable-nudges false, :username "cathalking"})
      (dunnit/add {:kind "user", :username "testuser", :email "cathalking1979@yahoo.com", :date (tc/to-date (t/now))})
      (dunnit/add {:kind "preferences", :notif-times [18, 21], :notif-days [5], 
                   :date (tc/to-date (t/now)), :disable-nudges true, :username "testuser"}))
    (println "No test data being added")))

(defn init [] 
  (try 
    (do
      (println "Doing init")
      (setup-test-data)
      (dunnit/process-latest-dunnit-emails true))
    (catch Exception e
      (println "Encountered error doing init. Exception was:")
      (. e printStackTrace)))
  )

(defn init-local [] 
  (do 
    (println "Doing init-local")
    (dunnit/load-sys-props sys-props-file)
    (setup-test-data)
    (dunnit/process-latest-dunnit-emails true)
  ))

(defn destroy [] 
  (println "Shutting down dunnit..."))

(defn destroy-local [] 
  (println "Shutting down dunnit"))

(defn wrap-auth-redirect [handler]
  (fn [{:keys [session uri] :as req}]
      (cond 
            (unauthenticated-routes uri)
                (handler req)
            (nil? (:user-details session))
              {:status 302 :flash {:redirect-to uri} :headers {"Location" "/login"}}
      :else (handler req))
    )
  )

; Compojure routes - no longer used. Sticking with some basic core.match based routing for now. 
; Opens up different options for makign routing decisions e.g. using arbritary request attributes
; But less capable  feature rich for working with RESTful URLs.
; May resurrect Compojure usage if I can't figure how to hand-roll elegant url-param matching+extraction
(defroutes app-routes

  (GET "/" [] 
    (r/redirect "/dunnit"))

  (GET "/login" [redirect-to] 
    (views/login-page (if (nil? redirect-to) "/dunnit" redirect-to)))

  (POST "/login" {:keys [session params]}
    {:status 302
     :session (assoc session :username (:username params))
     :headers {"Location" (if (nil? (:redirect-to params)) "/dunnit" (:redirect-to params))}})

  (GET "/logout" [] 
    {:status 302, :session nil, :headers {"Location" "/login"}})

  (GET "/dunnit" []
    (views/done-home-page :dones (dunnit/all-dones)))

  (POST "/dunnit" {:keys [session params]}
    (if (not (blank? (:done params)))
      (dunnit/add {:done (:done params) :message-id "N/A" :date (t/now) :from (:username session) :client "Dunnit Website"}))
    (r/redirect-after-post "/dunnit"))

  (GET "/customtables" []
    (views/custom-table-creator :dones (dunnit/all-dones) :tables @tables))

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
            ;(dunnit/persist-in dunnit/pub-sub-messages {:gmail-notif gmail-notif, :raw-message (:message pub-sub-message)})
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
  ;(route/resources "/")
  ;(route/not-found "Not Found")
)
