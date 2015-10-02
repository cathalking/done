(ns done.dunnit
  (:require 
    [done.online :refer :all]
    ;[done.offline :refer :all]
    [done.http :as http]
    [done.in-memory :as persist]
    [done.dummy-cache :as cache]
    ;[done.datastore :as persist]
    ;[done.cache :as cache]
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [clj-time.format :as tf]
    [clj-time.coerce :as tc]
            ))

;(def notifications (ref []))
;(def pub-sub-messages (ref []))
;(def emails (ref []))

(defn app-domain [] (System/getProperty "app-domain"))
(defn label-dunnit-test [] (System/getProperty "label-dunnit-test"))
(defn label-dunnit-new [] (System/getProperty "label-dunnit-new"))
(defn label-dunnit-processed [] (System/getProperty "label-dunnit-processed"))

(defn load-sys-props [file]
  (let [creds-map (json/parse-string (slurp file))]
    (doall
      (for [[k v] creds-map] (System/setProperty k v)))
    "System properties loaded"
    ))

(defn decode-msg [msg]
  (try
    (->> (b64/decode (.getBytes msg))
       (map char)
       (reduce str))
    (catch Exception e "Decoding error"))
    )

(defn decode-msg2 [msg]
  (try
    (->> (b64/decode ((.getBytes "UTF-8") msg))
       (map char)
       (reduce str))
    (catch Exception e "Decoding error"))
    )

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

; not yet used 
(defn get-all-message-content [label]
  (doall ; to realise the lazy sequences returned by map
    (->> (get-all-message-ids label)
         (map #(get-message %))
         (map extract-message-content))))

(defn email-text-to-dones [text-plain]
  (if (nil? text-plain) [] (clojure.string/split-lines text-plain)))

(defn extract-date [date-str]
  (first (clojure.string/split date-str #" \(UTC\)")))

(defn keywordize-kvps [m]
  (into {}
        (mapcat #(assoc {} (keyword (:name %)) (:value %)) m)))

(defn parse-date [date-str]
  (try (tf/parse (tf/formatters :rfc822) date-str)
    (catch Exception e (str "Failed to parse" date-str ". Error was: " (.getMessage e)))))

(defn process-dunnit 
  ([message-id] (process-dunnit message-id false false))
  ([message-id process?] (process-dunnit message-id process? false))
  ([message-id process? log?]
    (let [email (get-message message-id log?)
          raw-text (extract-message-content email)
          headers (->> (get-in email [:body :payload :headers]) keywordize-kvps)
          new-dones (email-text-to-dones raw-text)
          ]
      (when process? (modify-message message-id ["INBOX" "UNREAD" (label-dunnit-new)] [(label-dunnit-processed)] log?))
      (doall (map 
                  #(let [done { :kind "done"
                                :done % 
                                :message-id message-id
                                :date (tc/to-date (parse-date (extract-date (:Date headers))))
                                ;:date (parse-date (extract-date (:Date headers)))
                                :from (-> (:From headers) (clojure.string/split #" \<") second (clojure.string/split #"\>") first)
                                :client (:X-Mailer headers)
                                :content-type (:Content-Type headers)
                                :content-encoding (:Content-Transfer-Encoding headers)}]
                      (persist/create done)
                      (cache/add (:message-id done) done)
                    )
                  new-dones)))
  ))

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
  ([log?] ; use doall to force the realisation of lazy sequences i.e. I want my side effects and I want them now please.
    (doall 
      (->> (get-all-message-ids (label-dunnit-new) log?)
          (map #(process-dunnit % true log?))))))
 
(defn all-dones []
  (persist/find-all))

(defn add [done]
  (do 
    (cache/add (str (:username done) (System/currentTimeMillis)) done)
    (persist/create done)))
