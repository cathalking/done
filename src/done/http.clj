(ns done.http
  (:require [clojure.walk :as walk]
            [cheshire.core :as json]
            )
  (:import (java.net URL)
           (java.io OutputStreamWriter InputStreamReader BufferedReader)))

(defn gae-get-req-oauth [url-str oauth-access-token]
  (let [url (new URL url-str)
        http-url-conn (let [connection (. url openConnection)]
                        (doto connection
                          (. setRequestProperty "Authorization" (str "Bearer " oauth-access-token))
                          (. setRequestMethod "GET")))]
    (let [status (. http-url-conn getResponseCode)]
      (println "Request: " url-str)
      (if (= 200 status)
          {:status 200 
           :body (walk/keywordize-keys 
                   (json/parse-string 
                     (slurp (. http-url-conn getInputStream))))}
          {:status status 
           :headers (. http-url-conn getHeaderFields)
           :response-message (. http-url-conn getResponseMessage) 
           :body (walk/keywordize-keys 
                   (json/parse-string 
                     (slurp (. http-url-conn getErrorStream))))})
      )
    )
  )

(defn gae-post-req-oauth [url-str json oauth-access-token]
  (let [url (new URL url-str)
        http-url-conn (let [connection (. url openConnection)]
                        (doto connection
                          (. setDoOutput true)
                          (. setRequestProperty "Content-Type" "application/json")
                          (. setRequestProperty "Authorization" (str "Bearer " oauth-access-token))
                          (. setRequestMethod "POST")))]
    (let [output-writer (new OutputStreamWriter (. http-url-conn getOutputStream))]
      (do
        (println "Request: " url-str)
        (doto output-writer (. write json) (. close))
        (let [status (. http-url-conn getResponseCode)]
          (if (= 200 status)
            { :status 200 
              :body (walk/keywordize-keys
                     (json/parse-string 
                       (slurp (. http-url-conn getInputStream))))}
            { :status status 
              :headers (. http-url-conn getHeaderFields)
              :response-message (. http-url-conn getResponseMessage) 
            :body (walk/keywordize-keys 
                    (json/parse-string 
                      (slurp (. http-url-conn getErrorStream))))})
              )
        )
      )
    )
  )

(defn gae-post-req [url-str payload content-type]
  (let [url (new URL url-str)
        http-url-conn (let [connection (. url openConnection)]
                        (doto connection
                          (. setDoOutput true)
                          (. setRequestProperty "Content-Type" content-type)
                          ;(. setRequestProperty "Authorization" (str "Bearer " oauth-access-token))
                          (. setRequestMethod "POST")))]
    (let [output-writer (new OutputStreamWriter (. http-url-conn getOutputStream))]
      (do
        (println "Request: " url-str)
        (doto output-writer (. write payload) (. close))
        (let [status (. http-url-conn getResponseCode)]
          (if (= 200 status)
            { :status 200 
              :body (walk/keywordize-keys 
                      (json/parse-string 
                        (slurp (. http-url-conn getInputStream))))}
            { :status status 
              :headers (. http-url-conn getHeaderFields)
              :response-message (. http-url-conn getResponseMessage) 
              :body (walk/keywordize-keys 
                      (json/parse-string 
                        (slurp (. http-url-conn getErrorStream))))})
              )
        )
      )
    )
  )

;(defn gae-get-req [url-str]
;  (let [url (new URL url-str)
;        http-url-conn (let [connection (. url openConnection)]
;                        (doto connection
;                          (. setDoOutput true)
;                          (. setRequestProperty "Content-Type" "application/json")
;                          (. setRequestMethod "GET")))]
;    (println "Request: " url-str)
;    (let [status (. http-url-conn getResponseCode)
;          body (json/parse-string (slurp (. http-url-conn getInputStream)))]
;      {:status status
;       :body body}
;    )))

