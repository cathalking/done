(ns done.views
  (:require
        [done.dunnit :as dunnit]
        [hiccup.page :refer :all]
        [hiccup.form :refer :all])
  )

(defn common-layout [title & body]
  (html5
    [:head
     [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css"}]
     [:title title]]
    [:body
     [:div.container
      ;[:nav.navbar.navbar-inverse.navbar-fixed-top]
      [:h1#content-title title]
      body
      ]
     ]))

(defn done-form
  ([action input-name]
   [:form {:action action :method "post"}
    [:input#example-1 {:type "text" :name input-name}]
    [:button {:type "submit"} "Ooosh!"]]))

(defn table-list [col]
  [:ul.list-group (for [x col] [:li.list-group-item (str x)])])

(defn form-list
  [action input-fields submit-text]
   [:form {:action action :method "post"}
    (for [i input-fields] [(keyword (str "input#" (:name i))) {:type "text" :name (:name i) :placeholder (:label i)}])
    [:button {:type "submit"} submit-text]])

(defn list-title [title]
  (conj [:h2#content-title ] title))

;(defn table ([title col]
;  [:h2#content-title title]
;  [:ul.list-group (for [x col] [:li.list-group-item (str x)])]
;  ))

(defn done-home-page [dones]
  (let [dunnits-summary-resp (:body (dunnit/get-messages-summary dunnit/label-dunnit-new))
        messages (select-keys dunnits-summary-resp [:messagesUnread :messagesTotal])]
  (common-layout "Dunnit"
    (done-form "/dunnit" "done")
    (list-title "My Dunnits")
    ;(table-list (doall (mapv str dones)))
    (table-list dones)
    (list-title "Other's Dunnits")
    (table-list @dunnit/other-dones)
    (list-title "Dunnit-labelled Emails")
    (table-list {:unprocessed-messages (get messages :messagesTotal)})
    (done-form "/processdunnit" "messageid")
    (list-title "Emails pulled using Notifications")
    (table-list @dunnit/emails)
    (list-title "Gmail API Notifications")
    (table-list @dunnit/notifications)
    (list-title "Pub-Sub Messages")
    (table-list @dunnit/pub-sub-messages)
    )
  )
 )
