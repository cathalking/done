(ns done.gmail-online
  (:require 
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [done.http :as http]
    [done.googleoauth :refer [gmail-api-headers]]
    [clj-time.format :as f])
  (:import 
    (java.util Properties)
    (java.io ByteArrayOutputStream)
    (javax.mail.internet MimeMessage InternetAddress)
    (javax.mail Session Message$RecipientType)
))

(defn api-domain [] (System/getProperty "gmail-api-domain"))

(def ping-payload {:message 
                    {:data "eyJoaXN0b3J5SWQiOjI3Mjc0NTAsImVtYWlsQWRkcmVzcyI6ImNhdGhhbGtpbmcxQGdtYWlsLmNvbSJ9" 
                     :attributes {} 
                     :message_id "2094491946782"}
                   :subscription "projects/done-1041/subscriptions/dones"})
(defn ping-dunnit
  ([] (ping-dunnit (System/getProperty "app-domain")))
  ([domain]
    (http/gae-post-req (str domain "/done") 
                  (json/generate-string {:message {:data "eyJoaXN0b3J5SWQiOjI3Mjc0NTAsImVtYWlsQWRkcmVzcyI6ImNhdGhhbGtpbmcxQGdtYWlsLmNvbSJ9",  
                                                   :attributes {}, 
                                                   :message_id "2094491946782"},
                                         :subscription "projects/done-1041/subscriptions/dones"})
                [["Content-Type" "application/json"]]
                true)))

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

(defn mime-email [to from subject body-text]
  (let [props (new Properties)
        session (Session/getDefaultInstance props nil)
        toAddress (new InternetAddress to)
        fromAddress (new InternetAddress from)]
    (doto (new MimeMessage session)
      (.setFrom fromAddress)
      (.setSubject subject)
      (.setText body-text)
      (.addRecipient Message$RecipientType/TO toAddress))
  ))

(defn mime-to-base64 [mime-message]
  (let [bytes-stream (new ByteArrayOutputStream)]
    (.writeTo mime-message bytes-stream)
    (String. (b64/encode (.toByteArray bytes-stream)) "UTF-8")
))

(defn send-email 
  ([& {:keys [to from subject body log?] :or {log? true}}] 
    (let [email (mime-email to from subject body)
          raw-b64-email (mime-to-base64 email)
          url-safe-b64-email (-> raw-b64-email 
                                (clojure.string/replace "/" "_")
                                (clojure.string/replace "+" "-"))]
      (http/gae-post-req
        (str (api-domain) "/users/me/messages/send")
          (json/generate-string {:raw url-safe-b64-email})
          (gmail-api-headers) log?)
    )))

(def sys-props-file "config.json")
