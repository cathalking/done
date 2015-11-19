(ns done.views
  (:require
        [clj-time.format :as tf]
        [clj-time.coerce :as tc]
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
        [:meta {:charset "utf-8"}]
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
                  [:li [:a {:href "/dunnitsearch"} "Search" ]]
                  [:li [:a {:href "/customtables"} "Custom Tables" ]]
                ] 
                ;[:p.text-info.nav.navbar-right "Welcome"]
                [:ul.nav.navbar-nav.navbar-right
                  [:li [:a {:href "/settings"} "Settings" ]]
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

(defn search-form [&{:keys [action input-name placeholder search-btn-text]}]
   [:form {:action action :method "post"}
    [:div.form-group.form-inline
      [:input.form-control {:type "text" :name input-name :placeholder placeholder }]
      [:button.btn.btn-default {:type "submit"} search-btn-text]]]
    )

(defn list-title [title]
  (conj [:h2#content-title ] title))

(defn default-col-frmtrs [coll] 
  (for [k (keys (first coll))] [k str]))

(defn all-col-frmtrs []
  (for [k [:done :client :date :from :username :message-id :kind :key]] 
    [k str]))

(defn table [& {:keys [data display-cols]}]
  [:table.table.table-bordered.table-hover.table-condensed
    [:tr 
      (for [[col-header _] display-cols]
        [:th (clojure.string/capitalize (name col-header))])
    ]
    (for [d data] 
      [:tr
        (let [display-cols-map (into {} display-cols)]
          (for [k (keys display-cols-map)]
            [:td ((get display-cols-map k) (get d k))])
        )
      ]
    )])

(defn grouping-tables-by 
  [& {:keys [group-by-fn coll display-cols title-frmtr sort-comprtr]
        :or {title-frmtr str 
             sort-cmprtr compare}}]
    (mapcat #(into [] %)
      (for [[group-key group] (group-by group-by-fn coll)]
        [(list-title (title-frmtr group-key))
         (table :data group :display-cols display-cols)])))

(defn fmt-date 
  ([date] (fmt-date "MMM dd" (tc/from-date date)))
  ([patt date] (tf/unparse (tf/formatter patt) (tc/from-date date))))

(defn date-comparator [d1 d2] 
  (.isAfter (tc/from-date (:date d1)) (tc/from-date (:date d2))))

(defn sorted-dones [dones]
  (sort date-comparator dones))

(defn day [d]
  (fmt-date "MMM dd" (:date d)))

(defn login-page []
  (common-layout "Dunnit" false
    [:h1 "Login"]
    [:form.form-horizontal {:action "/login" :method "post"}
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

(defn login-page-google []
  (common-layout "Dunnit/Login" false
    [:h1 "Login"]
    [:a.btn.btn-default.btn-lg {:href "/oauth2/google"} "Login with Google"]
  ))

(defn done-home-page [& {:keys [dones user-details]}]
  (let [dones (sorted-dones dones)
        done-fields (->> (first dones) keys)]
    (common-layout "Dunnit/Home" true
      [:div.container
        [:h1 "Welcome " (:given_name user-details)]
        [:img.img-responsive.img-thumbnail {:src (:picture user-details) :style "width: 100px; height: 100px;"}]
      ]
      [:h1 "Dun owt?"]
      (done-form "/dunnit" "done")
      (when (not (empty? dones))
        [:h1 "By WhoDunnit"])
      (grouping-tables-by :group-by-fn :username
                           :coll dones 
                           :display-cols {:date (partial fmt-date "MMM dd") 
                                          :done str 
                                          :message-id str})
      (when (not (empty? dones))
        [:h1 "By Dundate"])
      (grouping-tables-by 
                           :group-by-fn day 
                           :coll dones 
                           :display-cols {:date (partial fmt-date "HH:mm") 
                                          :username str
                                          :done str
                                          :message-id str} 
                           :title-frmtr str 
                           :sort-cmprtr date-comparator)
      (when (not (empty? dones))
        (list-title "All Dunnits"))
      (when (not (empty? dones))
        (table :data dones :display-cols (all-col-frmtrs)))
      )
  ))

;(defn done-search-page [& {:keys [dones user-email-results username-results user-details results]}]
(defn done-search-page [& {:keys [dones results user-details]}]
  (let [dones (sorted-dones dones)]
    (common-layout "Dunnit/Search" true
      [:h2 "Search"]
      [:form.form-horizontal {:action "/dunnitsearch" :method "post"}
        [:div.form-group
          [:label.col-sm-2.control-label "Search type"]
          [:div.col-sm-5.btn-group 
            [:label.radio-inline [:input {:type "radio" :name "type" :value "done" }] "Done" ]
            [:label.radio-inline [:input {:type "radio" :name "type" :value "username" }] "Username" ]
            [:label.radio-inline [:input {:type "radio" :name "type" :value "user-email" }] "Email" ]]]
        [:div.form-group
          [:label.col-sm-2.control-label "Criteria"]
          [:div.col-sm-5
            [:input.form-control {:type "text" :name "search-text" :placeholder "Enter exact match"}]]]
        [:div.form-group
          [:div.col-sm-offset-2.col-sm-10
            [:button.btn.btn-default {:type "submit"} "Search"]]]
      ]
      (when (not (empty? results))
        (list-title "Et voilá!"))
      (table :data results :display-cols (default-col-frmtrs results))
      )
  ))

(defn custom-table-creator [& {:keys [dones tables]}]
  (let [dones (sorted-dones dones)
        done-fields (->> (first dones) keys)
        grouped-tables (filter #(not (nil? (:grouping (val %)))) tables)
        flat-tables (filter #(nil? (:grouping (val %))) tables)]
    (common-layout "Dunnit/Custom Tables" true
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
                  ;css-class "checkbox-inline"
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
                  ;css-class "radio-inline"
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
            (grouping-tables-by 
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
          (table :data (take 5 dones) :display-cols (for [k (:cols (val t))] [k str]))]
        ))
      )
  ))

(defn persist-test [] 
  (common-layout "Dunnit/Persist Test" true
    [:h1 "Create Entity"]
    [:form.form-horizontal {:action "/persist" :method "post"}
      [:div.form-group
        [:label.col-sm-2.control-label "Create Entity"]
        [:div.col-sm-5
          [:input.form-control {:type "text" :name "done" :placeholder "I know he used to do sh*t.. but wat has he dun for u lately!?"}]
        ]
      ]]
  ))

(defn settings-page [&{:keys [user-details user-settings errors] :or {user-details {} 
                                                        errors {}
                                                        user-settings {:times [12] :days [1 2 3 4 5] :emails ["cathalking1@gmail.com" "cathal.king@cmegroup.com"]}}}]
  (common-layout "Dunnit/Settings" true
    [:h2 "Preferences"]
    [:form.form-horizontal {:action "/settings" :method "post"}
      [:div.form-group
        [:label.col-sm-2.control-label "Nudge me at" [:p.text-muted "(optional)"]]
        [:div.col-sm-10.btn-group {:data-toggle "buttons"}
          (let [input-type "checkbox"
                ;css-class "checkbox-inline"
                input-cols [["Sun" 7] ["Mon" 1] ["Tue" 2] ["Wed" 3] ["Thu" 4] ["Fri" 5] ["Sat" 6]]
                input-name nil]
            (for [[k v] input-cols]
                  [(keyword (str "label.btn.btn-primary.btn-sm"))
                        [:input {:type "checkbox" :name "days" :value v}] k])
            )
        ]
        [:div.col-sm-10.btn-group {:data-toggle "buttons"}
          (let [input-type "checkbox"
                ;css-class "checkbox-inline"
                input-cols [["09:00" 9] ["12:00" 12] ["15:00" 15] ["18:00" 18] ["21:00" 21]]
                input-name nil]
            (for [[k v] input-cols]
                  [(keyword (str "label.btn.btn-primary.btn-sm"))
                        [:input {:type "checkbox" :name "times" :value v}] k])
            )
        ]]
      [:div.form-group
        [:div.col-sm-offset-2.col-sm-10
          [:input {:type "checkbox" :name "no-nudges"} "Stop sending nudges please"]
        ]]
      (when (not (empty? (:nudge-times errors)))
        [:div.form-group
          [:div.col-sm-offset-2.col-sm-10 [:p.text-danger (:nudge-times errors)]]
        ])
      [:div.form-group
        [:div.col-sm-offset-2.col-sm-10
          [:button.btn.btn-default {:type "submit"} "Save"]
        ]
      ]]
  ))

(defn admin-page [users]
  (common-layout "Dunnit/Admin" true
    [:h2 "Admin"]
    (when (not (empty? users))
      (list-title "All users registered for nudges"))
    (table :data users :display-cols (default-col-frmtrs users))
    [:form.form-horizontal {:action "/admin" :method "post"}
      [:div.form-group
        [:div.col-sm-offset-2.col-sm-10
          [:button.btn.btn-default {:type "submit"} "Send All Nudges"]
        ]
      ]
    ]
  ))

(defn registration-page 
  [&{:keys [user-details user-settings errors] :or {user-details {}, 
                                      errors {}
                                      user-settings {:times [12] :days [1 2 3 4 5] :emails ["cathalking1@gmail.com" "cathal.king@cmegroup.com"]}}}]
  (common-layout "Dunnit/Registration" false
    [:h2 "Complete Registration"]
    [:form.form-horizontal {:action "/register" :method "post"}
      [:div.form-group
        [:label.col-sm-2.control-label "Username"]
        [:div.col-sm-3
          [:input.form-control {:type "text" :name "username" :placeholder "Choose a username"}]
        ]
        (if (:username errors) [:p.text-danger (:username errors)])
      ]
      [:div.form-group
        [:div.col-sm-offset-2.col-sm-10
          [:button.btn.btn-default {:type "submit"} "Register"]
        ]
      ]
    ]
  ))

(defn add-email [email]
  (common-layout "Dunnit/Add Email" true
    [:h2 "Add New Email"]
    [:p.text-info "A confirmation email has been sent to " email]
    [:p.text-info "Click on the link it contains to complete setup."] 
    ;[:a.btn.btn-default.btn-lg {:href "/settings"} "Back"]
  ))
