(ns done.dunnit
  (:require 
    [clojure.data.codec.base64 :as b64]
    [cheshire.core :as json]
    [done.http :as http]
    [done.gmailauth :refer [gmail-api-headers]]
            ))

(def dones (ref []))
(def notifications (ref []))
(def pub-sub-messages (ref []))
(def emails (ref []))
(def other-dones (ref ["Nothing done"]))

(defn api-domain [] (System/getProperty "gmail-api-domain"))
(defn label-dunnit-new [] (System/getProperty "label-dunnit-new"))
(defn label-dunnit-processed [] (System/getProperty "label-dunnit-processed"))

(defn load-sys-props [file]
  (let [creds-map (json/parse-string (slurp file))]
    (doall
      (for [[k v] creds-map] (System/setProperty k v)))
    "System properties loaded"
    ))

(defn decode-msg [msg]
  (->> (b64/decode(.getBytes msg))
       (map char)
       (reduce str)))

(defn persist-in [col payload]
    (if (or (nil? payload) (empty? payload))
      @col 
      (dosync (alter col conj payload)))
    )

(defn history 
  ([history-id] (history history-id false))
  ([history-id log?]
    (http/gae-get-req
      (str (api-domain) "/users/me/history?labelId=" (label-dunnit-new) "&startHistoryId=" history-id)
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

(defn get-messages-summary 
  ([label] (get-messages-summary label false))
  ([label log?]
    (http/gae-get-req
      (str (api-domain) "/users/me/labels/" label)
    ( gmail-api-headers) log?)))

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

(defn extract-message-content [message]
  (->> (get-in message [:body :payload :parts])
      (filter #(= (:mimeType %) "text/plain"))
      (map #(get-in % [:body :data]))
      (map decode-msg)
      (first)
     )
  )

; not yet used 
(defn get-all-message-content [label]
  (doall ; to realise the lazy sequences returned by map
    (->> (get-all-message-ids label)
         (map #(get-message %))
         (map extract-message-content))))

(defn email-text-to-dones [text-plain]
  (if (nil? text-plain) [] (clojure.string/split-lines text-plain)))

(defn process-dunnit 
  ([message-id] (process-dunnit message-id false))
  ([message-id log?]
    (let [process-resp (modify-message message-id ["UNREAD" (label-dunnit-new)] [(label-dunnit-processed)] log?)
        raw-text (extract-message-content (get-message message-id))
        new-dones (email-text-to-dones raw-text)]
      (persist-in emails {:message-id message-id :text raw-text :dones new-dones})
      (if (or (nil? new-dones) (empty? new-dones)) 
        []
        (map #(persist-in dones %) new-dones))))
  )

(defn process-previous-dunnit [message-id]
    (let [raw-text (extract-message-content (get-message message-id))
          new-dones (email-text-to-dones raw-text)]
      (persist-in emails {:message-id message-id :text raw-text :dones new-dones})
      (if (or (nil? new-dones) (empty? new-dones)) 
        []
        (map #(persist-in dones %) new-dones)))
  )

(defn process-previous-dunnit-emails []
  (doall ; force the realisation of lazy sequences i.e. I want my side effects and I want them now please.
    (->> (get-all-message-ids (label-dunnit-processed) true)
       (map #(process-previous-dunnit %)))))

(defn process-latest-dunnit-emails []
  (doall ; force the realisation of lazy sequences i.e. I want my side effects and I want them now please.
    (->> (get-all-message-ids (label-dunnit-new))
         (map #(process-dunnit % true)))))
 
