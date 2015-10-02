(ns done.cache
  (:import (com.google.appengine.api.memcache MemcacheServiceFactory MemcacheService$SetPolicy)))

(def cache-service (MemcacheServiceFactory/getMemcacheService))

(defn add [done-key done]
  (let [cached-val (.get cache-service done-key)]
    (if (nil? cached-val) 
      (.put cache-service done-key done nil MemcacheService$SetPolicy/SET_ALWAYS)
      false)))

(defn stats []
  (let [stats (.getStatistics cache-service)]
    {:hit-count (.getHitCount stats)
     :miss-count (.getMissCount stats)
     :item-count (.getItemCount stats)}))
