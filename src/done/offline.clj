(ns done.offline
  (:require 
    [cheshire.core :as json]
    [done.dunnit :as dunnit]
            ))

(def all-emails 
  (do (println "Slurping all emails")
    (->> (slurp "/var/tmp/emails.json") 
       (cheshire.core/parse-string) 
       (clojure.walk/keywordize-keys))))

(def emails-by-id
  (->> (group-by #(get-in % [:body :id]) all-emails)))

(defn get-all-message-ids 
  ([label] (get-all-message-ids label false))
  ([label log?]
   (for [email all-emails 
         labelId (get-in email [:body :labelIds]) 
         :when (= labelId label)] 
     (get-in email [:body :id]))
  ))

(defn get-message 
  ([message-id] (get-message message-id false))
  ([message-id log?]
   (do 
    (first (get emails-by-id message-id)))))

(defn modify-message 
  ([message-id labels-to-remove labels-to-add] (modify-message message-id labels-to-remove labels-to-add false))
  ([message-id labels-to-remove labels-to-add log?]
    (println "Modified message" message-id)))
