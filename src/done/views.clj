(ns done.views
  (:require
        [clj-time.local :as tl]
        [clj-time.format :as tf]
        [done.dunnit :as dunnit]
        [hiccup.page :refer :all]
        [hiccup.util :as hu]
        [hiccup.form :refer :all])
  )

(defn sorted-dones [dones]
  (sort #(.isAfter (:date %1) (:date %2)) dones))

(defn common-layout [title & body]
  (html5
    [:head
     (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css")
     ;[:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css" :type "text/css"}]
     [:title title]]
    [:body
     [:div.container
      ;[:nav.navbar.navbar-inverse.navbar-fixed-top]
      body
      ]
     ]))

(defn done-form
  ([action input-name]
   [:form {:action action :method "post"}
    [:div.form-group.form-inline
      [:input#example-1.form-control {:type "text" :name input-name :placeholder "Dooooit!" }]
      [:button.btn.btn-default {:type "submit"} "Dunnit!"]]]
    ))

(defn table-list 
  ([col] (table-list col str))
  ([col f]
    [:ul.list-group (for [x col] [:li.list-group-item (f x)])]))

(defn table [dones]
   [:table.table.table-bordered.table-hover.table-condensed
     [:tr 
      [:th "Dunwhen?"] 
      [:th "Dunwat?"] 
      [:th "Dunster"]
      [:th "Email Id"] 
      ]
      (for [d dones] 
        (let [tr (if (or 
                       (nil? (:date d))
                       (nil? (:from d))
                       (nil? (:message-id d))
                       (nil? (:done d))
                       (= "Decoding error" (:done d))
                       ) 
                   :tr.danger :tr)]
          [tr
            [:td (tf/unparse (tf/formatter "MMM dd HH:mm") (:date d))] 
            [:td (:done d)] 
            [:td (hu/escape-html (:from d))] 
            [:td (:message-id d)] 
          ]))])

(defn table-per-person [[person dones]]
   [:table.table.table-bordered.table-hover.table-condensed
     [:tr 
      [:th "Dunwhen?"] 
      [:th "Dunwat?"] 
      [:th "Email Id"] 
      ]
      (for [d dones] 
        (let [tr (if (or 
                       (nil? (:date d))
                       (nil? (:message-id d))
                       (nil? (:done d))
                       (= "Decoding error" (:done d))
                       ) 
                   :tr.danger :tr)]
          [tr
            [:td (tf/unparse (tf/formatter "MMM dd HH:mm") (:date d))] 
            [:td (:done d)] 
            [:td (:message-id d)] 
          ]))])

(defn table-per-date [[date dones]]
   [:table.table.table-bordered.table-hover.table-condensed
     [:tr 
      [:th "Dunwhen?"] 
      [:th "Dunwat?"] 
      [:th "Dunster"] 
      [:th "Email Id"] 
      ]
      (for [d dones] 
        (let [tr (if (or 
                       (nil? (:from d))
                       (nil? (:message-id d))
                       (nil? (:done d))
                       (= "Decoding error" (:done d))
                       ) 
                   :tr.danger :tr)]
          [tr
            [:td (tf/unparse (tf/formatter "HH:mm") (:date d))]
            [:td (:done d)] 
            [:td (:from d)] 
            [:td (:message-id d)] 
          ]))])


(defn list-title [title]
  (conj [:h2#content-title ] title))

(defn group-by-date [dones]
  (mapcat #(into [] %)
    (for [group (group-by #(tf/unparse (tf/formatter "MMM dd") (:date %)) dones)]
      [(list-title (first group))
        (table-per-date group)])))

(defn group-by-person [dones]
  (mapcat #(into [] %)
    (for [group (group-by :from dones)]
      [(list-title (first group))
        (table-per-person group)])))

(defn form-list
  [action input-fields submit-text]
   [:form {:action action :method "post"}
    (for [i input-fields] [(keyword (str "input#" (:name i))) {:type "text" :name (:name i) :placeholder (:label i)}])
    [:button {:type "submit"} submit-text]])

;(defn table ([title col]
;  [:h2#content-title title]
;  [:ul.list-group (for [x col] [:li.list-group-item (str x)])]
;  ))

(defn format-dones [dones]
  (for [done (sort #(.isAfter (:date %1) (:date %2)) dones)]
    (str "(" (tl/to-local-date-time (:date done)) ") " 
         (:done done)
         " (" (:from done) ")"
         " (" (:client done) ")"
         )))

(defn done-home-page [dones]
  (let [dunnits-summary-resp (:body (dunnit/get-messages-summary dunnit/label-dunnit-new))
        messages (select-keys dunnits-summary-resp [:messagesUnread :messagesTotal])]
  (common-layout "Dunnit"
    ;[:h1#content-title "Dunnit"]
    (done-form "/dunnit" "done")
    [:h1 "By Dunster"]
    (group-by-person (sorted-dones dones))
    (list-title "By DunDate")
    (group-by-date (sorted-dones dones))
    (list-title "All Dunnits")
    (table (sorted-dones dones))
    ;(list-title "Dunnit-labelled Emails")
    ;(table-list {:unprocessed-messages (get messages :messagesTotal)})
    ;(done-form "/processdunnit" "messageid")
    ;(list-title "Emails pulled using Notifications")
    ;(table-list @dunnit/emails)
    ;(list-title "Gmail API Notifications")
    ;(table-list @dunnit/notifications)
    ;(list-title "Pub-Sub Messages")
    ;(table-list @dunnit/pub-sub-messages)
    )
  )
 )
