(ns done.persist
  (:import (com.google.appengine.api.datastore DatastoreServiceFactory Entity Key Query Query$FilterOperator))
  (:refer-clojure :exclude [get update]))

(declare entity-to-map query persist-in get-coll-ref datastoreService)

(defn- by-persistence-impl [_] 
  (System/getProperty "persistence-type"))

(defmulti create by-persistence-impl)
(defmulti find-all (fn [_ _] (System/getProperty "persistence-type")))
(defmulti update (fn [_ _] (System/getProperty "persistence-type")))
(defmulti delete (fn [_] (System/getProperty "persistence-type")))

;  "Retrieves the identified entity or raises EntityNotFoundException."
;(defmethod get "datastore" [#^Key key]
;  (entity-to-map (.get (datastoreService) key)))

;  "Executes the given com.google.appengine.api.datastore.Query
;  and returns the results as a lazy sequence of items converted with entity-to-map."
(defmethod find-all "datastore" [kind filters]
    (let [#^Query query (query kind filters)
          data-service (datastoreService)
          results (.asIterable (.prepare data-service query))]
      (map entity-to-map results)))

;  "Takes a map of keyword-value pairs and puts a new Entity in the Datastore.
;  The map must include a :kind String.
;  Returns the saved Entity converted with entity-to-map (which will include the assigned :key)."
(defmethod create "datastore" [item]
  ;([item] (create item nil))
  ;([item #^Key parent-key]
  (let [kind (item :kind)
        parent-key nil
        properties (dissoc item :kind)
        entity (if parent-key (Entity. kind parent-key) (Entity. kind))]
    (doseq [[prop-name value] properties] (.setProperty entity (name prop-name) value))
    (.put (datastoreService) entity)
    (entity-to-map entity)))

(defmethod update "datastore" [entity-key props] 
  (let [entity (.get (datastoreService) entity-key)]
    (doseq [[k v] props] (.setProperty entity (name k) v))
    (.put (datastoreService) entity)
    (entity-to-map entity)))

;  "Deletes the identified entities."
(defmethod delete "datastore" [& the-keys]
  (.delete (datastoreService) the-keys))

(def dones (atom []))
(def users (atom []))
(def preferences (atom []))

(defmethod create "in-memory" [item]
  (let [kind (:kind item)
        entity item]
    (persist-in (get-coll-ref kind) entity)))

(defmethod find-all "in-memory" [kind filters]
  (let [coll (get-coll-ref kind)]
    (filter (fn [entity] (= filters 
                        (select-keys entity (keys filters)))) @coll)))

(defmethod update "in-memory" [entity-key props] 
  (let [entity nil]
    nil))

(defn- entity-to-map
  "Converts an instance of com.google.appengine.api.datastore.Entity
  to a PersistentHashMap with properties stored under keyword keys,
  plus the entity's kind stored under :kind and key stored under :key."
  [#^Entity entity]
  (let [m (reduce #(assoc %1 (keyword (key %2)) (val %2))
            {:kind (.getKind entity) :key (.getKey entity)}
            (.entrySet (.getProperties entity)))]
    m
  ))

(defn- query [kind filters]
  (let [query (Query. kind)]
    (doseq [[k v] filters] 
      (.addFilter query (name k) Query$FilterOperator/EQUAL v))
    query
  ))

(defn persist-in [col payload]
  (if (or (nil? payload) (empty? payload))
    @col 
    (swap! col conj payload))
  )

(defn get-coll-ref [kind] 
  (cond (= kind "user") users
        (= kind "done") dones
        (= kind "preferences") preferences
        :else (throw (IllegalArgumentException. (str "Unrecognised entity kind: " kind)))))

(defn- datastoreService [] 
  (DatastoreServiceFactory/getDatastoreService))

