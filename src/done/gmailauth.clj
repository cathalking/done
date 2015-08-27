(ns done.gmailauth
  (:require 
    [cheshire.core :as json]
    [done.http :as http]
    [gmail-clj.core :as gm]))

(def oauth-creds (ref {}))
(def oauth-token (ref {}))

(defn as-form-params [m]
  (->> (map (fn [[k v]] (str k"="v)) m)
       (clojure.string/join \&))
  )

(defn load-creds [file]
  (dosync (alter oauth-creds merge @oauth-creds (json/parse-string (slurp file)))))

(defn authcreds [] 
  (let [
        refresh_token (if (nil? (get @oauth-creds "refresh_token")) 
                        (System/getProperty "gmail-api-refresh-token") (get @oauth-creds "refresh_token"))
        client_id (if (nil? (get @oauth-creds "client_id"))
                        (System/getProperty "gmail-api-client-id") (get @oauth-creds "client_id"))
        client_secret (if (nil? (get @oauth-creds "client_secret"))
                        (System/getProperty "gmail-api-client-secret") (get @oauth-creds "client_secret"))
        grant_type (if (nil? (get @oauth-creds "grant_type")) (System/getProperty "gmail-api-grant-type")
                        (get @oauth-creds "grant_type"))
        creds { :refresh_token refresh_token
                :client_id client_id
                :client_secret client_secret
                :grant_type grant_type}]
      (dosync (alter oauth-creds merge @oauth-creds creds))
    ))

(defn oauth-form-params []
  (as-form-params (authcreds)))

(defn refresh-oauth-token []
  (let [resp (http/gae-post-req "https://accounts.google.com/o/oauth2/token" 
                                                (oauth-form-params) "application/x-www-form-urlencoded") 
        new-token (select-keys (:body resp)[:access_token :expires_in])]
    (println "Refreshing oauth-token")
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
