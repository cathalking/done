(ns done.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [done.handler :refer [app]]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 303))
      (is (= (get (:headers response) "Location") "/login" ))
      ;(is (= (:body response) "Hello World"))
  ))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 303)))))
