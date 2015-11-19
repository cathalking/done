(ns done.dunnit
  (:require 
    [done.online :refer :all]
    ;[done.offline :refer :all]
    ;[done.in-memory :as persist]
    [done.datastore :as persist]
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [clj-time.format :as tf]
    [clj-time.coerce :as tc])
  (:import (com.google.api.client.repackaged.org.apache.commons.codec.binary Base64))
)

;(def notifications (ref []))
;(def pub-sub-messages (ref []))
;(def emails (ref []))

(defn app-domain [] (System/getProperty "app-domain"))
(defn label-dunnit-test [] (System/getProperty "label-dunnit-test"))
(defn label-dunnit-new [] (System/getProperty "label-dunnit-new"))
(defn label-dunnit-error [] (System/getProperty "label-dunnit-error"))
(defn label-dunnit-processed [] (System/getProperty "label-dunnit-processed"))

(defn load-sys-props [file]
  (let [creds-map (json/parse-string (slurp file))]
    (doall
      (for [[k v] creds-map] (System/setProperty k v)))
    "System properties loaded"
    ))

(defn decode-msg [base64-url-safe-msg]
  (let [base64-msg (-> base64-url-safe-msg 
                       (clojure.string/replace "-" "+")
                       (clojure.string/replace "_" "\\"))
        msg-bytes (b64/decode (.getBytes base64-msg))]
    (String. msg-bytes (java.nio.charset.Charset/forName "UTF-8"))
    ;(try
      ;(->> (b64/decode (.getBytes base64-msg))
      ;     (map char)
      ;     (reduce str))
    ;  (catch Exception e "Decoding error"))
  ))

(defn reset-test-messages []
  (->> (get-all-message-ids (label-dunnit-test))
       (map #(modify-message % [(label-dunnit-processed)] [(label-dunnit-new)]))))

(defn seq-contains? [coll target] (some #(= target %) coll))

(defmulti extract-message-content (fn [email] (get-in email [:body :payload :mimeType])))

(defmethod extract-message-content "multipart/alternative" [email]
  (->> (get-in email [:body :payload :parts])
      (filter #(= (:mimeType %) "text/plain"))
      first
      :body 
      :data
      decode-msg
     ))

(defmethod extract-message-content "text/plain" [email]
  (->> (get-in email [:body :payload :body :data])
       decode-msg
     ))
  ;(decode-msg (get-in email [:body :payload :body :data])))

#(def dunnit-regex #"\"dunnitinbox\+new@gmail\.com\" <dunnitinbox\+new@gmail\.com> wrote:")
(def dunnit-regex #" <dunnitinbox\+new@gmail\.com> wrote:")

(defn email-text-to-dones [text-plain]
  (if (nil? text-plain) 
    [] 
    (->> (clojure.string/split-lines text-plain)
         (take-while #(not (re-find dunnit-regex %)))
         (filter #(not (clojure.string/blank? %))))
  ))

(defn extract-date [date-str]
  (first (clojure.string/split date-str #" \(UTC\)")))

(defn keywordize-kvps [m]
  (into {}
        (mapcat #(assoc {} (keyword (:name %)) (:value %)) m)))

(defn parse-date [date-str]
  (try (tf/parse (tf/formatters :rfc822) date-str)
    (catch Exception e (str "Failed to parse" date-str ". Error was: " (.getMessage e)))))

(defn search [criteria record-type]
  (cond
    (= "done" record-type)
      (persist/find-all (persist/query "done" {:done criteria}))
    (= "username" record-type)
      (persist/find-all (persist/query "user" {:username criteria}))
    (= "user-email" record-type)
      (persist/find-all (persist/query "user" {:email criteria}))
  ))

(defn search-users [user-email]
  (persist/find-all (persist/query "user" {:email user-email})))

(defn extract-from [headers]
  (let [from-split (-> (:From headers) (clojure.string/split #" \<"))]
    (if (= 2 (count from-split)) 
      (-> (:From headers) (clojure.string/split #" \<") second (clojure.string/split #"\>") first)
      (:From headers)
      )))

(defn process-dunnit 
  ([message-id] (process-dunnit message-id false false))
  ([message-id process?] (process-dunnit message-id process? false))
  ([message-id process? log?]
    (try 
      (let [email (get-message message-id log?)
            raw-text (extract-message-content email)
            headers (->> (get-in email [:body :payload :headers]) keywordize-kvps)
            from (extract-from headers)
            user (first (search-users from))
            done-date (tc/to-date (parse-date (extract-date (:Date headers))))
            new-dones (email-text-to-dones raw-text)
            ]
        (when process? (modify-message message-id ["INBOX" "UNREAD" (label-dunnit-new)] [(label-dunnit-processed)] log?))
        (if (nil? user)
          (println "Found no registered user with email address: " from)
          (doseq [done-text new-dones]
            (persist/create 
                  { :kind "done"
                    :done done-text
                    :message-id message-id
                    :date done-date
                    ;:date (parse-date (extract-date (:Date headers)))
                    :from from
                    :username (:username user)
                    :client (:X-Mailer headers)
                    :content-type (:Content-Type headers)
                    :content-encoding (:Content-Transfer-Encoding headers)})))
              ;(persist/create done)
              ;new-dones)))
    )
    (catch Exception e
      (do
        (println "Encountered error processing message" message-id " - will attempt to tag with gmail error label. Exception was:")
        (. e printStackTrace)
        (modify-message message-id ["INBOX" "UNREAD" (label-dunnit-new)] [(label-dunnit-error)] log?))
    ))))

(defn get-all-email-dates []
  (for [message-id (get-all-message-ids (label-dunnit-processed) false)
        header (get-in (get-message message-id false) [:body :payload :headers]) 
        :when (= "Date" (:name header))]
        (:value header)
        ))

(defn process-previous-dunnit-emails 
  ([] (process-previous-dunnit-emails false))
  ([log?] ; use doall to force the realisation of lazy sequences i.e. I want my side effects and I want them now please.
    (doall 
      (->> (get-all-message-ids (label-dunnit-processed) log?)
         (map #(process-dunnit % false log?))))))

(defn process-latest-dunnit-emails 
  ([] (process-latest-dunnit-emails false))
  ([log?]
    (let [message-ids (get-all-message-ids (label-dunnit-new) log?)]
      (doseq [message-id (get-all-message-ids (label-dunnit-new) log?)]
        (process-dunnit message-id true log?))
      (println "Processed" (count message-ids) "dunnit emails"))
  ))
 
(defn all-dones []
  (persist/find-all (persist/query "done" {})))

(defn all-users []
  (persist/find-all (persist/query "user" {})))

(defn all-user-prefs []
  (persist/find-all (persist/query "preferences" {})))

(defn all-nudge-users []
  (persist/find-all (persist/query "preferences" {:disable-nudges false})))

(defn search-nudge-users [notif-time]
  (persist/find-all (persist/query "preferences" {:notif-times notif-time, :disable-nudges false})))

(defn search-dones [done-text]
  (persist/find-all (persist/query "done" {:done done-text})))

(defn search-dones-by-user [username]
  (persist/find-all (persist/query "done" {:username username})))

(defn search-usernames [username]
  (persist/find-all (persist/query "user" {:username username})))

(defn search-prefs [username]
  (persist/find-all (persist/query "preferences" {:username username})))

(defn update [entity-key props]
  (persist/update entity-key props))

(defn add [record]
  (do 
    ;(cache/add (str (:username done) (System/currentTimeMillis)) done)
    (persist/create record)))

(defn send-nudges [] 
  (let [all-nudge-user-prefs (all-nudge-users)]
    (println "Found" (count all-nudge-user-prefs) "user(s) registered for nudges")
    (doseq [user-pref all-nudge-user-prefs]
      (let [user (first (search-usernames (:username user-pref)))
            user-email (:email user)]
        (println "Sending nudge to" user-email)
        (send-email :to user-email
                    :from "dunnitinbox+new@gmail.com" 
                    :subject "Nudge nudge - what'd you get done today?" 
                    :body "Hit reply and jot down what you've got done today!")
                  ;:body "Each line of your reply will be logged as a Done...Now get to it"))))
      ))
  ))

(defn send-nudges2 [nudge-time] 
  (let [all-nudge-user-prefs (search-nudge-users nudge-time)]
    (println "Found" (count all-nudge-user-prefs) "user(s) registered for nudges at " nudge-time)
    (doseq [user-pref all-nudge-user-prefs]
      (let [user (first (search-usernames (:username user-pref)))
            user-email (:email user)]
        (println "Sending nudge to" user-email)
        (send-email :to user-email
                    :from "dunnitinbox+new@gmail.com" 
                    :subject "Nudge nudge - what'd you get done today?" 
                    :body "Hit reply and jot down what you've got done today!")
                  ;:body "Each line of your reply will be logged as a Done...Now get to it"))))
      ))
  ))
