(ns done.gmailauth
  (:require 
    [cheshire.core :as json]
    [done.http :as http]
     ))

(defn oauth-creds [] {:refresh_token (System/getProperty "gmail-api-refresh-token") 
                  :client_id (System/getProperty "gmail-api-client-id") 
                  :client_secret (System/getProperty "gmail-api-client-secret") 
                  :grant_type (System/getProperty "gmail-api-grant-type")})

(def oauth-token (ref {:access_token nil :expires_in nil}))
(def google-oauth-api-headers [["Content-Type" "application/x-www-form-urlencoded"]])

(defn as-form-params [m]
  (->> (clojure.walk/stringify-keys m)
       (map (fn [[k v]] (str k"="v)))
       (clojure.string/join \&))
  )

(defn oauth-form-params [] (as-form-params (oauth-creds)))

(defn now [] (quot (System/currentTimeMillis) 1000))

(defn refresh-oauth-token 
  ([] (refresh-oauth-token false))
  ([log?]
    (let [resp (http/gae-post-req "https://accounts.google.com/o/oauth2/token" 
                                                (oauth-form-params) google-oauth-api-headers log?)
        access-token (get-in resp [:body :access_token])
        expires-in (+ (now) (get-in resp [:body :expires_in]))]
      (if (= 200 (:status resp)) (println "Refreshed oauth-token") (println "Failed to refresh token. Response was: " (:body resp)))
      (dosync (alter oauth-token merge @oauth-token {:access_token access-token :expires_in expires-in}))
    )
  ))

(defn get-token
  "Returns the current active auth token."
  []
  ;(when (or (empty? @oauth-token)
  (when (or (nil? (:access_token @oauth-token))
            (> (now) (get @oauth-token :expires_in)))
      (refresh-oauth-token))
  (get @oauth-token :access_token))  

(defn gmail-api-headers []
  [
   ["Content-Type" "application/json"]
   ["Authorization" (str "Bearer " (get-token))]])

