(ns done.handler
  (:require [compojure.core :refer :all]
            [done.idonethis :as idt]
            [compojure.route :as route]
            [cheshire.core :as json]
            [hiccup.page :refer :all]
            [hiccup.form :refer :all]
            [ring.util.response :as r]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(def dones (ref ["Process Code Review" "Attend Planning Meeting"]))

(defn common-layout [& body]
  (html5
    [:head
     [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css"}]
     [:title "Dunnit"]]
    [:body
     [:div.container
     ;[:nav.navbar.navbar-inverse.navbar-fixed-top]
     [:h1#content-title "Dunnit"]
      body
      ]
     ]))

(defn done-form
  ([]
   (done-form "/dunnit" "done"))
  ([action input-name]
    [:form {:action action :method "post"}
      [:input#example-1 {:type "text" :name input-name}]
      [:button {:type "submit"} "Ooosh!"]]))

(defn example-post [request]
  (let [post-value (get-in request [:params :example-post])]
    (str "You posted: " post-value)))

(defn example-post2 [post-param]
  (str "You posted: " post-param))

(defroutes app-routes
  (GET "/get" []
    (common-layout (done-form "/post" "example-post")))
  (POST "/post" [example-post]
    (example-post2 example-post))
  (GET "/home" []
    (common-layout [:p "Welcome to Dunnit"]))
  (GET "/dunnit" []
    (common-layout
       (done-form)
       [:ul.list-group (for [x @dones] [:li.list-group-item x])]
      ))
  (GET "/alldunnits" []
    (json/generate-string (deref dones)))
  (POST "/dunnit" [done]
    (dosync (alter dones conj done))
    (r/redirect-after-post "/dunnit"))
  (GET "/idonethese" []
    (json/generate-string (idt/get-dones)))
  (route/not-found
    "Not Found"))
  (comment
    (GET "/dones-as-map" []
      {:status 200
        :headers {"Content-Type" "application/json"}
        :body (json/generate-string (idt/get-dones))})
    (GET "/dones-as-map2" []
      (-> (r/response (json/generate-string (idt/get-dones)))
          (r/content-type "application/json")))
    (GET "/session-counter" [session]
       (let [count   (:count session 0)
             session (assoc session :count (inc count))]
             (-> (r/response (str "You accessed this page " count " times."))
                 (assoc :session session)
                 (json/generate-string)))))



(def app
  (wrap-defaults app-routes (assoc-in site-defaults [:security :anti-forgery] false)))
