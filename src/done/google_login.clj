(ns done.google-login
 (:use compojure.core)
 (:require [done.http :as client]
           [done.googleoauth :refer [oauth2-web-app-creds as-request-params google-oauth2-api-headers]]
           [done.dunnit :as dunnit]
           [ring.util.codec]
           [cheshire.core :as json]
           ;[noir.response :as resp]
))
 
(defn REDIRECT_URI [] (str (dunnit/app-domain) "/oauth2/callback"))
(def google-user (atom {:google-id "" :google-name "" :google-email ""}))
 
(defn request-user-to-authorize [& {:keys [client_id scopes original-uri]}] 
  (str "https://accounts.google.com/o/oauth2/auth?"
    (as-request-params {:scope (->> scopes (clojure.string/join " ") (ring.util.codec/url-encode))
                     :redirect_uri (ring.util.codec/url-encode (REDIRECT_URI))
                     :response_type "code"
                     :state original-uri ; this value will be returned. Handy way to remember where to redirect to after the Oauth2 hops are complete.
                     :client_id (ring.util.codec/url-encode client_id)
                     :approval_prompt "auto"
    })))
 
(defn request-access-token [auth-code]
  (client/gae-post-req "https://accounts.google.com/o/oauth2/token"
    (as-request-params {:code auth-code
                     :client_id (:client_id (oauth2-web-app-creds))
                     :client_secret (:client_secret (oauth2-web-app-creds))
                     :redirect_uri (REDIRECT_URI)
                     :grant_type "authorization_code"}) 
    google-oauth2-api-headers true))

(defn request-user-details [access-token]
  (client/gae-get-req (str "https://www.googleapis.com/oauth2/v1/userinfo?access_token=" access-token)
                      [["Content-Type" "application/json"]] true))
      ;(let [user-details (:body user-details-resp)]
        ;(swap! google-user 
        ;  #(assoc % :google-id %2 :google-name %3 :google-email %4) (:id user-details) (:name user-details) (:email user-details)))))
 
