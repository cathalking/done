(ns done.dunnit
  (:require 
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [done.http :as http]
    [done.gmailauth :refer [get-token]]
            ))

(def dones (ref []))
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
    (if (or (nil? payload) (empty? payload))
      @col 
      (dosync (alter col conj payload)))
    )

(def api-domain (if (nil? (System/getProperty "gmail-api-domain")) "https://www.googleapis.com/gmail/v1" (System/getProperty "gmail-api-domain")))

(defn history [history-id]
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/history?labelId=" label-dunnit-new "&startHistoryId=" history-id)
    (get-token)))

(defn list-all-dunnits [label]
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/messages?labelIds=" label)
    (get-token)))

(defn dunnits-summary []
  (http/gae-get-req-oauth 
    (str api-domain "/users/me/labels/" label-dunnit-new)
    (get-token)))

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
  (->> (get-in (list-all-dunnits label-dunnit-new) [:body :messages])
       (map #(get-message (:id %)))
       (map extract-message-content)))

(defn process-dunnit [message-id]
    (let [process-resp (http/gae-post-req-oauth 
                    (str api-domain "/users/me/messages/" message-id "/modify")
                        (json/generate-string {"removeLabelIds" ["UNREAD", label-dunnit-new]
                                               "addLabelIds" [label-dunnit-processed] }) 
                        (get-token))
          raw-text (extract-message-content (get-message message-id))
          new-dones (if (nil? raw-text) [] (clojure.string/split-lines raw-text))]
      (persist-in emails {:message-id message-id :text raw-text :dones new-dones})
      (println "New-dones: " new-dones " nil or empty? "(or (nil? new-dones) (empty? new-dones)))
      (if (or (nil? new-dones) (empty? new-dones)) [] (map #(persist-in dones %) new-dones))
    )
  )

(defn process-previous-dunnit-emails []
  (->> (get-in (list-all-dunnits label-dunnit-processed) [:body :messages])
       (map #(process-dunnit (:id %)))))

(defn process-latest-dunnit-emails []
  (->> (get-in (list-all-dunnits label-dunnit-new) [:body :messages])
       (map #(process-dunnit (:id %)))))
 
