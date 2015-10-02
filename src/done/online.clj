(ns done.online
  (:require 
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [done.http :as http]
    [done.googleoauth :refer [gmail-api-headers]]
    [clj-time.format :as f]
            ))

(defn api-domain [] (System/getProperty "gmail-api-domain"))

(defn get-message 
  ([message-id] (get-message message-id false))
  ([message-id log?]
    (http/gae-get-req
      (str (api-domain) "/users/me/messages/" message-id)
      (gmail-api-headers) log?)))

(defn modify-message 
  ([message-id labels-to-remove labels-to-add] (modify-message message-id labels-to-remove labels-to-add false))
  ([message-id labels-to-remove labels-to-add log?]
    (http/gae-post-req
      (str (api-domain) "/users/me/messages/" message-id "/modify")
        (json/generate-string {:removeLabelIds labels-to-remove
                              :addLabelIds labels-to-add })
        (gmail-api-headers) log?)))

(defn get-all-message-ids 
  ([label] (get-all-message-ids label false))
  ([label log?]
  (let [resp (http/gae-get-req
              (str (api-domain) "/users/me/messages?labelIds=" label)
              (gmail-api-headers) log?)
        messages (get-in resp [:body :messages])
        message-ids (map :id messages)]
    message-ids
  )))

(defn history 
  ([history-id label] (history history-id label false))
  ([history-id label log?]
    (http/gae-get-req
      (str (api-domain) "/users/me/history?labelId=" label "&startHistoryId=" history-id)
      (gmail-api-headers) log?)))

(def sys-props-file "/var/tmp/creds.dunnitinbox.json")
