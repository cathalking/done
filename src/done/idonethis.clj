(ns done.idonethis
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def idonethis-apikey "beaba1b7b199edb0e46a4661b503027ae86fa6f3")

(def default-api-args
  {:headers {:Accept-Type "application/json" :Authorization (str "Token " idonethis-apikey)}
   :as      :json})

(def dones-api-url "https://idonethis.com/api/v0.1/dones/")

(defn get-dones
  ([] (get-dones dones-api-url))
  ([url] (-> (http/get url default-api-args) :body)))

(defn no-more-dones? [dones-data] (nil? (:next dones-data)))
;(defn no-more-dones? [dones-data] (= (:next dones-data) "https://idonethis.com/api/v0.1/dones/?page=4&page_size=100"))

(defn get-all-dones []
  (loop [dones-data (get-dones (str dones-api-url "?page=1&page_size=100"))
         dones-acc []]
    (println (str (:next dones-data) " dones-acc.count=" (count dones-acc) " dones-data.count=" (count (:results dones-data))))
    (if (no-more-dones? dones-data)
      (concat dones-acc (:results dones-data))
      (recur (get-dones (:next dones-data)) (concat dones-acc (:results dones-data)))

      )))

(def es-instance "localhost:9200")

(defn save-done-es [done]
  (http/put (str "http://" es-instance "/idonethis/dones/" (:id done))
              {:body (json/generate-string done)}))

(defn get-done-es [id] 
  (:_source (:body 
              (http/get (str "http://" es-instance "/idonethis/dones/" id) {:as :json}))))

; Find First phrase (delimited by colon) of done.raw_text and update the done.tags value of iDoneThis record (or the elastic Search record for starters) with the generated value
; todo - Enhance tag generation rules e.g. what if no : is found? What if exceeds X words?
(defn generate-tag [done] (first (clojure.string/split (:raw_text done) #":")))

(defn tag-done [done] (assoc done :tags (conj (:tags done) {:id 0 :name (generate-tag done)})))

; Example usage to pull in all 'Dones', do some data-enrichment (just tagging) and then store as-is in elasticsearch
;(map save-done-es (map tag-done (get-all-dones)))


;(count (get-all-dones))

