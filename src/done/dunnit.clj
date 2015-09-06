(ns done.dunnit
  (:require 
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [done.http :as http]
    [done.online :refer :all]
    [done.gmailauth :refer [gmail-api-headers]]
    [clj-time.format :as f]
            ))

(def dones (ref []))
(def notifications (ref []))
(def pub-sub-messages (ref []))
(def emails (ref []))

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
    (->> (b64/decode(.getBytes msg))
       (map char)
       (reduce str))
    (catch Exception e "Decoding error"))
    )

(defn persist-in [col payload]
    (if (or (nil? payload) (empty? payload))
      @col 
      (dosync (alter col conj payload)))
    )

(defn reset-test-messages []
  (->> (get-all-message-ids (label-dunnit-test))
       (map #(modify-message % [(label-dunnit-processed)] [(label-dunnit-new)]))))

(defmulti extract-message-content (fn [email] (get-in email [:body :payload :mimeType])))

(defmethod extract-message-content "multipart/alternative" [email]
  (->> (get-in email [:body :payload :parts])
      (filter #(= (:mimeType %) "text/plain"))
      first
      :body 
      :data
      decode-msg
     ))

(defn seq-contains? [coll target] (some #(= target %) coll))

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
  (try (f/parse (f/formatters :rfc822) date-str)
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
      (when process? (modify-message message-id ["UNREAD" (label-dunnit-new)] [(label-dunnit-processed)] log?))
      (persist-in emails {:message-id message-id :text raw-text :dones new-dones})
      (doall (map #(persist-in dones 
                               {:done % 
                                :message-id message-id
                                :date (parse-date (extract-date (:Date headers)))
                                :from (-> (:From headers) (clojure.string/split #" \<") second (clojure.string/split #"\>") first)
                                :client (:X-Mailer headers)})
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
      (->> (get-all-message-ids (label-dunnit-new))
          (map #(process-dunnit % true log?))))))
 
