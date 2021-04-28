(ns sv.peer-sci.prewarm
  "Pulls all datoms of all indexes from the storage into the memory of
   this peer. This allows to start a peer with a hot cache (if the
   memory is large enough to hold the complete db)."
  (:require [datomic.api :as d]
            [sv.peer-sci.connection :as con]))

(defn prewarm-index!
  "Forces that the datom is pulled from the storage into the memory of
   the peer."
  [db index-id]
  (doall
   (seq (d/datoms db
                  index-id))))

(def index-ids
  [:eavt
   :aevt
   :avet
   :vaet])

(defn prewarm!
  "Pulls all datoms of all indexes from the storage into the memory of
   this peer."
  []
  (let [db (con/db)]
    (into {}
          (map
           (fn [[index-id datoms-future]]
             [index-id (count (deref datoms-future))])
           (doall
            (map (fn [index-id]
                   [index-id
                    (future (prewarm-index! db
                                            index-id))])
                 index-ids))))))

(comment
  (time (prewarm!))
  )

