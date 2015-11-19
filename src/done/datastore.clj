(ns done.datastore
  (:import (com.google.api.services.datastore.client DatastoreHelper)
           (com.google.appengine.api.datastore DatastoreServiceFactory Entity Key Query Query$FilterOperator))
  (:refer-clojure :exclude [get]))

;(def datastore (DatastoreHelper/getDatastoreFromEnv))


(defn entity-to-map
  "Converts an instance of com.google.appengine.api.datastore.Entity
  to a PersistentHashMap with properties stored under keyword keys,
  plus the entity's kind stored under :kind and key stored under :key."
  [#^Entity entity]
  (let [m (reduce #(assoc %1 (keyword (key %2)) (val %2))
            {:kind (.getKind entity) :key (.getKey entity)}
            (.entrySet (.getProperties entity)))]
    (println m)
    m
  ))

(defn get
  "Retrieves the identified entity or raises EntityNotFoundException."
  [#^Key key]
  ;(entity-to-map (.get datastore key)))
  (entity-to-map (.get (DatastoreServiceFactory/getDatastoreService) key)))

(defn find-all
  "Executes the given com.google.appengine.api.datastore.Query
  and returns the results as a lazy sequence of items converted with entity-to-map."
  [#^Query query]
    (let [data-service (DatastoreServiceFactory/getDatastoreService)
          results (.asIterable (.prepare data-service query))]
      (map entity-to-map results)))

(defn query [kind filters]
  (let [query (Query. kind)]
    (doseq [[k v] filters] 
      (.addFilter query (name k) Query$FilterOperator/EQUAL v))
    query
  ))

(defn create
  "Takes a map of keyword-value pairs and puts a new Entity in the Datastore.
  The map must include a :kind String.
  Returns the saved Entity converted with entity-to-map (which will include the assigned :key)."
  ([item] (create item nil))
  ([item #^Key parent-key]
    (let [kind (item :kind)
          properties (dissoc item :kind)
          entity (if parent-key (Entity. kind parent-key) (Entity. kind))]
      (doseq [[prop-name value] properties] (.setProperty entity (name prop-name) value))
      (.put (DatastoreServiceFactory/getDatastoreService) entity)
      (entity-to-map entity))))

(defn update [entity-key props] 
  (let [entity (.get (DatastoreServiceFactory/getDatastoreService) entity-key)]
    (doseq [[k v] props] (.setProperty entity (name k) v))
    (.put (DatastoreServiceFactory/getDatastoreService) entity)
    (entity-to-map entity)))

(defn delete
  "Deletes the identified entities."
  [& the-keys]
  (.delete (DatastoreServiceFactory/getDatastoreService) the-keys))

