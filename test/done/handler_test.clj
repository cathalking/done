(ns done.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [done.handler :refer [app]]
            [done.dunnit :refer [load-sys-props]]
            [done.gmail-online :refer [ping-payload sys-props-file]]
            ))

(defn req [&{:keys [method uri body content-type]}]
  (-> (mock/request method uri)
      (mock/body body)
      (mock/content-type content-type)
      ))

(comment 

(defn setup-sys-props [f] 
  (load-sys-props sys-props-file)
  (f))

(use-fixtures :once setup-sys-props)

(def ping-json-payload (json/generate-string ping-payload))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 303))
      (is (= (get (:headers response) "Location") "/login" ))
  ))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 303))))
  )
  
(deftest test-app2
  (testing "post /done"
    (let [response (app (req :method :post, 
                             :uri "/done", 
                             :body ping-json-payload, 
                             :content-type "application/json"
                             ))]
      (is (= (:status response) 200))
    ))

  (testing "get /dunnit (unauthenticated)"
    (let [response (app (req :method :get, 
                             :uri "/dunnit"
                             ))]
      (is (= (:status response) 
             303))
      (is (= (get-in response [:headers "Location"]) 
             "/login"))
    ))
  ))
