(ns done.gmailauth
  (:require 
    [cheshire.core :as json]
    [done.http :as http]
    [gmail-clj.core :as gm]))

(def oauth-creds (ref {}))
(def oauth-token (ref {}))


(defn authcreds [] 
  (let [
        refresh_token (if (nil? (:refresh_token @oauth-creds)) 
                        (System/getProperty "gmail-api-refresh-token") (:refresh_token @oauth-creds))
        client_id (if (nil? (:client_id @oauth-creds))
                        (System/getProperty "gmail-api-client-id") (:client_id @oauth-creds))
        client_secret (if (nil? (:client_secret @oauth-creds))
                        (System/getProperty "gmail-api-client-secret") (:client_secret @oauth-creds))
        grant_type (if (nil? (:grant_type @oauth-creds)) (System/getProperty "gmail-api-grant-type")
                        (:grant_type @oauth-creds))
        creds { :refresh_token refresh_token
                :client_id client_id
                :client_secret client_secret
                :grant_type grant_type}]
      (dosync (alter oauth-creds merge @oauth-creds creds))
    ))

(defn as-form-params [m]
  (->> (clojure.walk/stringify-keys m)
       (map (fn [[k v]] (str k"="v)))
       (clojure.string/join \&))
  )

(defn load-creds [file]
  (dosync (alter oauth-creds merge @oauth-creds (json/parse-string (slurp file) true))))

(defn oauth-form-params []
  (as-form-params (authcreds)))

(defn refresh-oauth-token []
  (let [resp (http/gae-post-req "https://accounts.google.com/o/oauth2/token" 
                                                (oauth-form-params) "application/x-www-form-urlencoded") 
        new-token (select-keys (:body resp)[:access_token :expires_in])]
    (if (= 200 (:status resp)) (println "Refreshed oauth-token") (println "Failed to refresh token. Response was: " (:body resp)))
    (dosync (alter oauth-token merge @oauth-token new-token))
    )
  )

(defn now [] 
  (quot (System/currentTimeMillis) 1000))

(defn get-token
  "Returns the current active auth token."
  []
  (when (or (empty? @oauth-token)
            (> (get @oauth-token :expires_in) (now)))
      (refresh-oauth-token))
  (get @oauth-token :access_token))  
