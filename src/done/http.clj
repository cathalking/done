(ns done.http
  (:require [clojure.walk :as walk]
            [cheshire.core :as json]
            )
  (:import (java.net URL)
           (java.io OutputStreamWriter InputStreamReader BufferedReader)))

(defn input-stream-to-map [input-stream]
  (walk/keywordize-keys (json/parse-string (slurp input-stream))))

(defn handle-response [http-url-conn]
  (let [status (. http-url-conn getResponseCode)]
    (if (= 200 status)
      { :status 200 
        :body (input-stream-to-map (. http-url-conn getInputStream))}
      { :status status 
        :headers (. http-url-conn getHeaderFields)
        :response-message (. http-url-conn getResponseMessage) 
        :body (input-stream-to-map (. http-url-conn getErrorStream))})
        ))

(defn set-headers [connection headers]
  (doall 
    (for [header headers] 
      (doto connection (.setRequestProperty (first header) (second header))))
    )
  )

(defn gae-get-req
  ([url-str headers] (gae-get-req url-str headers true))
  ([url-str headers log?]
    (let [http-url-conn (let [connection (. (new URL url-str) openConnection)]
                          (doto connection
                            ( . setRequestMethod "GET"))
                          (set-headers connection headers)
                          connection)
        ;status (. http-url-conn getResponseCode)
        resp (handle-response http-url-conn)] 
      (when log? (println "Request:" url-str " Response:" resp))
      resp
  )))

(defn gae-post-req 
  ([url-str payload headers] (gae-post-req url-str payload headers true))
  ([url-str payload headers log?]
   (let [http-url-conn (let [connection (. (new URL url-str) openConnection)]
                        (doto connection
                          (. setDoOutput true)
                          (. setRequestMethod "POST"))
                        (set-headers connection headers)
                        connection)
        output-writer (new OutputStreamWriter (. http-url-conn getOutputStream))
        ]
      (do
        (doto output-writer (. write payload) (. close))
        (let [resp (handle-response http-url-conn)]
          (when log? (println "Request:" url-str " Response:" resp))
          resp
        )))
   )
  )

