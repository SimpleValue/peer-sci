(ns sv.peer-sci.main
  (:require [org.httpkit.server :as server]
            [sv.peer-sci.state :as state]
            [sv.peer-sci.nrepl :as nrepl]
            [sv.peer-sci.core :as core]
            [datomic.api :as d])
  (:gen-class))

(defn index-handler
  [request]
  (when (and (= (:request-method request)
                :get)
             (= (:uri request)
                "/"))
    {:status  200
     :headers {"Content-Type" "text/plain"}
     :body    "sv.peer-sci"}))

(defn handler [req]
  (some
   (fn [handler]
     (handler req))
   [index-handler
    core/ring-handler]))

(defn start!
  []
  (swap! state/state
         assoc
         :datomic/con
         (d/connect (System/getenv "DATOMIC_URI"))
         :nrepl/server
         (nrepl/start!)
         :server
         (server/run-server #'handler
                            {:port (Long/valueOf
                                    (or (System/getenv "PORT")
                                        "8000"))})))

(defn stop!
  []
  (when-let [stop-server (:server @state/state)]
    (stop-server))
  (when-let [nrepl (:nrepl/server @state/state)]
    (nrepl/stop! nrepl)))

(defn -main
  [& args]
  (start!)
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread.
                     (fn []
                       (stop!))))
  )

(comment
  (start!)
  (stop!)
  )
