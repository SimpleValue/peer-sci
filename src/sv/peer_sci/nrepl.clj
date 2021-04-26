(ns sv.peer-sci.nrepl
  (:require [nrepl.server :as nrepl]))

(defn start!
  []
  (let [config [:bind "0.0.0.0"
                :port 4000]]
    (apply nrepl/start-server
           config)))

(defn stop!
  [nrepl-server]
  (nrepl/stop-server nrepl-server))
