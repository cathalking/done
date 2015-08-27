(ns done.dunnit
  (:require 
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [done.http :as http]
    [done.gmailauth :refer [get-token]]
            ))

(def dones (ref ["Done1" "Done2"]))
(def notifications (ref []))
(def pub-sub-messages (ref []))
(def emails (ref []))
(def other-dones (ref ["Nothing done"]))

(def domain (System/getProperty "domain"))
(def protocol (System/getProperty "protocol"))
(def gmail-api-domain (System/getProperty "gmail-api-domain"))
(def gmail-api-history (System/getProperty "gmail-api-history"))

(def label-dunnit-new "Label_24")
(def label-dunnit-processed "Label_23")

(defn app-path [uri]
  (str protocol "://" domain uri))

(defn decode-msg [msg]
  (->> (b64/decode(.getBytes msg))
       (map char)
       (reduce str)))

(defn persist-in [col payload]
  (do 
    (println "Persisting " payload)
    (dosync (alter col conj payload))
    ))

(def api-domain (if (nil? (System/getProperty "gmail-api-domain")) "https://www.googleapis.com/gmail/v1" (System/getProperty "gmail-api-domain")))

(defn history [history-id]
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/history?labelId=" label-dunnit-new "&startHistoryId=" history-id)
    (get-token)))

; list all messages matching Dunnit label
; https://www.googleapis.com/gmail/v1/users/me/messages?labelIds=Label_22
(defn list-all-dunnits [label]
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/messages?labelIds=" label)
    (get-token)))

; https://www.googleapis.com/gmail/v1/users/me/labels/Label_22
(defn dunnits-summary []
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/labels/" label-dunnit-new)
    (get-token)))

; get message
; https://www.googleapis.com/gmail/v1/users/me/messages/14f6111566fcab09
(defn get-message [message-id]
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/messages/" message-id)
    (get-token)))

(defn extract-message-content [message]
  (->> (get-in message [:body :payload :parts])
      (filter #(= (:mimeType %) "text/plain"))
      (map #(get-in % [:body :data]))
      (map decode-msg)
      (first)
     )
  )

(defn get-latest-dunnit-messages []
  (->> (get-in (list-all-dunnits) [:body "messages"])
       (map #(get-message (get % "id")))
       (clojure.walk/keywordize-keys)
       (map extract-message-content)))

; POST https://www.googleapis.com/gmail/v1/users/me/messages/14f6111566fcab09/modify
; { "removeLabelIds": [ "UNREAD", "Label_22" ] }
(defn process-dunnit [message-id]
  (do
    ;(println "Calling " (str api-domain "/users/me/messages/" message-id "/modify"))
    (http/gae-post-req-oauth 
      (str api-domain "/users/me/messages/" message-id "/modify")
      (json/generate-string {"removeLabelIds" ["UNREAD", label-dunnit-new]
                             "addLabelIds" [label-dunnit-processed] 
                             }
                            ) 
      (get-token))
    (persist-in emails {:message-id message-id :text (extract-message-content (get-message message-id))})
    )
  )

(defn process-latest-dunnit-emails []
  (->> (get-in (list-all-dunnits) [:body "messages"])
       (map #(process-dunnit (get % "id")))))
 
