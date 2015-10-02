(ns done.googleoauth
  (:require 
    [cheshire.core :as json]
    [done.http :as http]
     ))

(defn oauth2-gmail-api-creds [] {:refresh_token (System/getProperty "dunnit-inbox-gmail-api-refresh-token") 
                                 :client_id (System/getProperty "dunnit-other-app-oauth2-client-id") 
                                 :client_secret (System/getProperty "dunnit-other-app-oauth2-client-secret") 
                                 :grant_type (System/getProperty "dunnit-other-app-oauth2-grant-type")})

(defn oauth2-web-app-creds [] {:client_id (System/getProperty "dunnit-web-app-oauth2-client-id") 
                               :client_secret (System/getProperty "dunnit-web-app-oauth2-client-secret")})

(def oauth2-access-token (ref {:access_token nil :expires_in nil}))
(def google-oauth2-api-headers [["Content-Type" "application/x-www-form-urlencoded"]])

(defn as-request-params [m]
  (->> (clojure.walk/stringify-keys m)
       (map (fn [[k v]] (str k"="v)))
       (clojure.string/join \&))
  )

(defn now [] (quot (System/currentTimeMillis) 1000))

(defn refresh-access-token 
  ([] (refresh-access-token false))
  ([log?]
    (let [resp (http/gae-post-req "https://accounts.google.com/o/oauth2/token" 
                                  (as-request-params (oauth2-gmail-api-creds)) 
                                  google-oauth2-api-headers 
                                  log?)
        access-token (get-in resp [:body :access_token])
        expires-in (+ (now) (get-in resp [:body :expires_in]))]
      (if (= 200 (:status resp)) (println "Refreshed gmail api access token") (println "Failed to refresh token. Response was: " (:body resp)))
      (dosync (alter oauth2-access-token merge @oauth2-access-token {:access_token access-token :expires_in expires-in}))
    )
  ))

(defn get-access-token
  "Returns the current active access token."
  []
  (when (or (nil? (:access_token @oauth2-access-token))
            (> (now) (get @oauth2-access-token :expires_in)))
      (refresh-access-token))
  (get @oauth2-access-token :access_token))  

(defn gmail-api-headers []
  [
   ["Content-Type" "application/json"]
   ["Authorization" (str "Bearer " (get-access-token))]])

