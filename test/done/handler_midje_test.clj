(ns done.handler-midje-test
  (:require [midje.sweet :refer [fact facts future-fact contains => with-state-changes]]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [done.handler-test :refer [req]]
            [done.handler :refer [app]]
            [done.dunnit :refer [load-sys-props]]
            [done.gmail-online :refer [ping-payload sys-props-file]]
            ))

(def ping-json-payload (json/generate-string ping-payload))

(with-state-changes [(before :facts (load-sys-props sys-props-file))]
  (facts "About handler"
    (fact "get /dunnit (unauthenticated) 2"
      (app (req :method :get, 
                :uri "/dunnit")) 
      => (contains {:status 303 
                    :headers (contains {"Location" "/login"})}))

    (fact "post /done"
      (app (req :method :post, 
                             :uri "/done", 
                             :body ping-json-payload, 
                             :content-type "application/json"
                             ))
      => (contains {:status 200}
    ))

    (future-fact "get /dunnit (authenticated) 2"
      (app (req :method :get, 
                :uri "/dunnit" 
                 )) => (contains {:status 200}))
  ))
