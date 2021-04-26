(ns sv.peer-sci.connection
  (:require [datomic.api :as d]
            [sv.peer-sci.state :as state]))

(defn con
  []
  (:datomic/con @state/state))

(defn db
  []
  (d/db (con)))
