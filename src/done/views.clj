(ns done.views
  (:require
        [clj-time.local :as tl]
        [clj-time.format :as tf]
        [done.dunnit :as dunnit]
        [hiccup.page :refer :all]
        [hiccup.util :as hu]
        [hiccup.form :refer :all])
  )

(defn common-layout [title & body]
  (html5
    [:head
     (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css")
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

(defn list-title [title]
  (conj [:h2#content-title ] title))

(defn default-col-frmtrs [coll] 
  (for [k (keys (first coll))] [k str]))

(defn table [coll cols]
  (let [cols-map (into {} cols)]
   [:table.table.table-bordered.table-hover.table-condensed
     [:tr 
      (for [[col-header _] cols]
        [:th (clojure.string/capitalize (name col-header))] )
      ]
      (for [d coll] 
        [:tr
          (for [k (map #(first %) cols)]
            [:td ((get cols-map k) (get d k))])
        ])]))

(defn grouping-tables-by 
  ([f coll] (grouping-tables-by f coll (default-col-frmtrs coll) str compare))
  ([f coll cols] (grouping-tables-by f coll cols str compare))
  ([f coll cols title-frmtr] (grouping-tables-by f coll cols title-frmtr compare))
  ([f coll cols title-frmtr comprtr]
    (mapcat #(into [] %)
      (for [group (group-by f coll)]
        [(list-title (title-frmtr (first group)))
         (table (second group) cols)]))))

(defn form-list
  [action input-fields submit-text]
   [:form {:action action :method "post"}
    (for [i input-fields] [(keyword (str "input#" (:name i))) {:type "text" :name (:name i) :placeholder (:label i)}])
    [:button {:type "submit"} submit-text]])

(defn fmt-date 
  ([date] (fmt-date "MMM dd" date))
  ([patt date] (tf/unparse (tf/formatter patt) date)))

(defn date-comparator [d1 d2] (.isAfter (:date d1) (:date d2)))

(defn sorted-dones [dones]
  (sort date-comparator dones))

(defn day [d]
  (fmt-date "MMM dd" (:date d)))

(defn done-home-page [dones]
  (let [dones (sorted-dones dones)]
    (common-layout "Dunnit"
      (done-form "/dunnit" "done")
      [:h1 "By WhoDunnit"]
      (grouping-tables-by :from dones [[:date (partial fmt-date "MMM dd")] [:done str] [:message-id str]])
      [:h1 "By Dundate"]
      (grouping-tables-by day dones [[:date (partial fmt-date "HH:mm")] [:done str] [:message-id str]] str date-comparator)
      (list-title "All Dunnits")
      (table dones (default-col-frmtrs dones))
      )
  ))

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
