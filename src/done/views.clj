(ns done.views
  (:require
        [clj-time.local :as tl]
        [clj-time.format :as tf]
        [done.dunnit :as dunnit]
        [hiccup.page :refer :all]
        [hiccup.util :as hu]
        [hiccup.form :refer :all])
  )

(defn common-layout 
  ;([title & body] (common-layout title true body))
  ([title show-navbar? & body]
    (html5
      [:head
        (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css")
        (include-js "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js")
        (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js")
        [:title title]]
      [:body
        (when show-navbar?
          [:nav.navbar.navbar-default.navbar-static-top
            [:div.container
              [:div.navbar-header 
                [:a.navbar-brand {:href "/dunnit"} "Dunnit!" ]] 
              [:div.navbar
                [:ul.nav.navbar-nav
                  [:li [:a {:href "/dunnit"} "Dunnits" ]]
                  [:li [:a {:href "/customtables"} "Custom Tables" ]]
                ] 
                ;[:p.text-info.nav.navbar-right "Welcome"]
                [:ul.nav.navbar-nav.navbar-right
                  [:li [:a {:href "/logout"} "Logout" ]]
                ]]
            ]
          ])
        [:div.container
          body
        ]
       ])))

(defn done-form [action input-name]
   [:form {:action action :method "post"}
    [:div.form-group.form-inline
      [:input.form-control {:type "text" :name input-name :placeholder "Dooooit!" }]
      [:button.btn.btn-default {:type "submit"} "Dunnit!"]]]
    )

(defn form1 [action input-type input-cols]
   [:form {:action action :method "post"}
    [:div.form-group
      (for [input input-cols]
        [:label.checkbox-inline 
         [:input {:type input-type :name (name input) }] 
         (->> (name input) clojure.string/capitalize) ])
        [:input.form-control {:type "text" :name "title" :placeholder "Title" }]
      [:button.btn.btn-default {:type "submit"} "Create table"]
    ]
   ])

(defn form3 [& {:keys [action input-type input-cols]}]
   [:form {:action action :method "post"}
    [:div.form-group
      (for [input input-cols]
        [:label.checkbox-inline 
         [:input {:type input-type :name (name input) }] 
         (->> (name input) clojure.string/capitalize) ])
      [:input.form-control {:type "text" :name "title" :placeholder "Title" }]
      [:button.btn.btn-default {:type "submit"} "Create table"]
    ]
   ])

(defn form4 [& {:keys [action input-fields]}]
   [:form {:action action :method "post"}
    [:div.form-group
      input-fields
      [:input.form-control {:type "text" :name "title" :placeholder "Title" }]
      [:button.btn.btn-default {:type "submit"} "Create table"]
    ]]
   )

(defn form-inputs [& {:keys [input-type input-cols css-class input-name]}]
  (for [input input-cols]
    [(keyword (str "label.control-label." css-class))
     [:input {:type input-type :name (if (nil? input-name) (name input) input-name)}] 
     (->> (name input) clojure.string/capitalize) ]))

(defn form-inputs2 [& {:keys [input-type input-cols css-class input-name]}]
  (for [input input-cols]
    [(keyword (str "label." css-class))
     [:input {:type input-type :name (if (nil? input-name) (name input)input-name) :value (name input)}] 
     (->> (name input) clojure.string/capitalize) ]))

(defn list-title [title]
  (conj [:h2#content-title ] title))

(defn default-col-frmtrs [coll] 
  (for [k (keys (first coll))] [k str]))

(defn table [coll cols]
  (let [cols-map (into {} cols)]
   [:table.table.table-bordered.table-hover.table-condensed
     [:tr 
      (for [[col-header _] cols]
        [:th (clojure.string/capitalize (name col-header))])
      ]
      (for [d coll] 
        [:tr
          (for [k (map #(first %) cols)]
            [:td ((get cols-map k) (get d k))])
        ])]))

(defn table2 [& {:keys [data display-cols]}]
  [:table.table.table-bordered.table-hover.table-condensed
    [:tr 
      (for [[col-header _] display-cols]
        [:th (clojure.string/capitalize (name col-header))])
    ]
    (for [d data] 
      [:tr
        (for [k (keys display-cols)]
          [:td ((get display-cols k) (get d k))])
      ]
    )])

(defn grouping-tables-by2 
  [& {:keys [group-by-fn coll display-cols title-frmtr sort-comprtr]
        :or {display-cols (default-col-frmtrs (first @done.dunnit/dones)) 
             title-frmtr str 
             sort-cmprtr compare}}]
    (mapcat #(into [] %)
      (for [[group-key group] (group-by group-by-fn coll)]
        [(list-title (title-frmtr group-key))
         (table2 :data group :display-cols display-cols)])))

(defn grouping-tables-by 
  ([f coll] (grouping-tables-by f coll (default-col-frmtrs coll) str compare))
  ([f coll cols] (grouping-tables-by f coll cols str compare))
  ([f coll cols title-frmtr] (grouping-tables-by f coll cols title-frmtr compare))
  ([f coll cols title-frmtr comprtr]
    (mapcat #(into [] %)
      (for [group (group-by f coll)]
        [(list-title (title-frmtr (first group)))
         (table (second group) cols)]))))

(defn fmt-date 
  ([date] (fmt-date "MMM dd" date))
  ([patt date] (tf/unparse (tf/formatter patt) date)))

(defn date-comparator [d1 d2] 
  (.isAfter (:date d1) (:date d2)))

(defn sorted-dones [dones]
  (sort date-comparator dones))

(defn day [d]
  (fmt-date "MMM dd" (:date d)))

(defn login-page [redirect-url]
  (common-layout "Dunnit" false
    [:h1 "Login"]
    [:form.form-horizontal {:action (str "/login?redirect-to=" redirect-url) :method "post"}
      [:div.form-group
        [:label.col-sm-2.control-label "Username"]
        [:div.col-sm-3
          [:input.form-control {:type "text" :name "username" :placeholder "Enter username"}]
        ]
      ]
      [:div.form-group
        [:div.col-sm-offset-2.col-sm-10
          [:button.btn.btn-default {:type "submit"} "Login"]
        ]
      ]
    ]
  ))

(defn custom-table-creator [& {:keys [dones tables]}]
  (let [dones (sorted-dones dones)
        done-fields (->> (first @done.dunnit/dones) keys)
        grouped-tables (filter #(not (nil? (:grouping (val %)))) tables)
        flat-tables (filter #(nil? (:grouping (val %))) tables)]
    (common-layout "Dunnit" true
      [:h1 "Create custom table"]
      [:form.form-horizontal {:action "/customtables" :method "post"}
        [:div.form-group
          [:label.col-sm-2.control-label "Table title" [:p.text-danger "(required)"]]
          [:div.col-sm-5
            [:input.form-control {:type "text" :name "title" :placeholder "Enter title of table"}]
          ]
        ]
        [:div.form-group
          [:label.col-sm-2.control-label "Display Cols" [:p.text-danger "(required)"]]
          [:div.col-sm-10.btn-group {:data-toggle "buttons"}
            (let [input-type "checkbox"
                  css-class "checkbox-inline"
                  input-cols done-fields
                  input-name nil]
              (for [input input-cols]
                    [(keyword (str "label.btn.btn-primary.btn-sm"))
                          [:input {:type input-type :name (if (nil? input-name) (name input) input-name)}]
                               (->> (name input) clojure.string/capitalize) ])
              )
          ]
        ]  
        [:div.form-group
          [:label.col-sm-2.control-label "Group By " [:p.text-muted "(optional)"]]
          [:div.col-sm-10.btn-group {:data-toggle "buttons"}
            (let [input-type "radio"
                  css-class "radio-inline"
                  input-cols done-fields
                  input-name "grouping"]
              (for [input input-cols]
                    [(keyword (str "label.btn.btn-default.btn-sm"))
                          [:input {:type input-type :name (if (nil? input-name) (name input) input-name) :value (name input)}]
                               (->> (name input) clojure.string/capitalize) ])
              )
          ]
        ]
        [:div.form-group
          [:div.col-sm-offset-2.col-sm-10
            [:button.btn.btn-default {:type "submit"} "Create table"]
          ]
        ]
      ]
      (when (not (empty? grouped-tables))
        [:h1 "Generated grouped tables"])
      (mapcat #(into [] %)
        (for [t grouped-tables]
          [[:h1 (key t)]
            (grouping-tables-by2 
                                 :group-by-fn (keyword (:grouping (val t)))
                                 :coll dones 
                                 :display-cols (into {} (for [col (:cols (val t))] [col str])))
            ]
        ))
      (when (not (empty? flat-tables)) 
        [:h1 "Generated tables"])
      (mapcat #(into [] %)
        (for [t flat-tables]
          [[:h1 (key t)]
          (table (take 5 dones) (for [k (:cols (val t))] [k str]))]
        ))
      )
  ))

(defn done-home-page [& {:keys [dones]}]
  (let [dones (sorted-dones dones)
        done-fields (->> (first @done.dunnit/dones) keys)]
    (common-layout "Dunnit" true
      [:h1 "Dun owt?"]
      (done-form "/dunnit" "done")
      ;[:h1 "By WhoDunnit"]
      ;(grouping-tables-by :from dones [[:date (partial fmt-date "MMM dd")] [:done str] [:message-id str]])
      [:h1 "By WhoDunnit"]
      (grouping-tables-by2 :group-by-fn :from 
                           :coll dones 
                           :display-cols {:date (partial fmt-date "MMM dd") 
                                          :done str 
                                          :message-id str})
      ;[:h1 "By Dundate"]
      ;(grouping-tables-by day dones  [[:date (partial fmt-date "HH:mm")] [:done str] [:message-id str]] str date-comparator)
      [:h1 "By Dundate"]
      (grouping-tables-by2 :group-by-fn day 
                           :coll dones 
                           :display-cols {:date (partial fmt-date "HH:mm") 
                                          :done str
                                          :message-id str} 
                           :title-frmtr str 
                           :sort-cmprtr date-comparator)
      (list-title "All Dunnits")
      (table dones (default-col-frmtrs dones))
      ;(form1 "/table" "checkbox" (->> (first @done.dunnit/dones) keys))
      ;(form1 "/table" "radio" (->> (first @done.dunnit/dones) keys))
      ;(form3 :action "/table" :input-type "checkbox" :input-cols (->> (first @done.dunnit/dones) keys))
      (comment 
        (form4 :action "/table" 
               :input-fields (form-inputs :input-type "radio" 
                                          :css-class "radio-inline" )
                                          :input-cols (->> (first @done.dunnit/dones) keys)))
      (comment
        (form4 :action "/table" 
               :input-fields 
                (concat (form-inputs :input-type "checkbox" 
                                     :css-class "checkbox-inline" 
                                     :input-cols (->> (first @done.dunnit/dones) keys))
                          (form-inputs :input-type "radio" 
                                :css-class "radio-inline" 
                                :input-cols (->> (first @done.dunnit/dones) keys)
                                ))))
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
