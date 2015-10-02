(ns done.ringdebug
  (:require [clojure.pprint :refer :all]))

; middleware for spying on the doings of other middleware:
(defn html-escape [string]
  (clojure.string/escape string {\< "&lt;", \> "&gt;"}))

(defn html-preformatted-escape [string]
  (str "<pre>\n" (html-escape string) "</pre>\n"))

(defn format-request [name request kill-keys kill-headers]
  (let [r1 (reduce dissoc request kill-keys)
        r (reduce (fn [h n] (update-in h [:headers] dissoc n)) r1 kill-headers)]
  (with-out-str
    (println "-------------------------------")
    (println name)
    (println "-------------------------------")
    (clojure.pprint/pprint r)
    (println "-------------------------------"))))


;; I have taken the liberty of removing some of the less fascinating entries from the request and response maps, for clarity
;(def kill-keys [:body :character-encoding :remote-addr :server-name :server-port :ssl-client-cert :scheme  :content-type  :content-length])
(def kill-keys [])
;(def kill-headers ["user-agent" "accept" "accept-encoding" "accept-language" "accept-charset" "cache-control" "connection"])
(def kill-headers [])

(defn wrap-spy [handler spyname & {:keys [log-req log-resp] :or {log-req true log-resp false}}]
  (fn [request]
    (let [incoming (format-request (str spyname ":\n Incoming Request:") request kill-keys kill-headers)]
      (when log-req (println incoming))
      (let [response (handler request)]
        (let [outgoing (format-request (str spyname ":\n Outgoing Response Map:") response kill-keys kill-headers)]
          (when log-resp (println outgoing))
          response
          ;(update-in response  [:body] (fn[x] (str (html-preformatted-escape incoming) x  (html-preformatted-escape outgoing))))
          )))))
