(ns done.in-memory)

(def dones (ref []))

(defn persist-in [col payload]
  (if (or (nil? payload) (empty? payload))
    @col 
    (dosync (alter col conj payload)))
  )

(defn find-all [] 
  @dones)

(defn create [item] 
  (persist-in dones item))

