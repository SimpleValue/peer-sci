(ns sv.peer-sci.core
  (:require [sci.core :as sci]
            [clojure.edn :as edn]
            [org.httpkit.server :as httpkit]
            [ring.util.response :as response]
            [clojure.stacktrace :as stacktrace]
            ))

(defn namespaces-import
  [namespaces]
  (into {}
        (map
         (fn [namespace]
           (require namespace)
           [namespace (ns-publics namespace)])
         namespaces)))

(def default-namespaces
  '[datomic.api
    sv.peer-sci.connection
    clojure.set
    clojure.walk
    clojure.string
    clojure.java.io
    clj-time.core
    clj-time.format
    clj-time.coerce
    ])

(def default-classes
  {'org.joda.time.LocalDateTime org.joda.time.LocalDateTime
   'org.joda.time.DateTimeZone org.joda.time.DateTimeZone
   'java.util.Date java.util.Date
   'System java.lang.System
   'Long java.lang.Long
   })

(defn eval-code
  [params]
  (sci/eval-string
   (:code params)
   {:namespaces (namespaces-import
                 (distinct
                  (concat
                   (:namespaces params)
                   default-namespaces)))
    :classes (merge default-classes
                    (into {}
                          (map
                           (fn [[sym class-sym]]
                             [sym (Class/forName (str class-sym))])
                           (:classes params))))}))

(comment
  (eval-code {:code (pr-str '(range 10))})
  )

(defn get-thread-pool-size
  []
  (* (.getAvailableProcessors
      (java.lang.management.ManagementFactory/getOperatingSystemMXBean))
     4))

(defonce executor
  (let [pool-size (get-thread-pool-size)
        queue (java.util.concurrent.ArrayBlockingQueue. pool-size)]
    (java.util.concurrent.ThreadPoolExecutor. pool-size
                                              pool-size
                                              1
                                              java.util.concurrent.TimeUnit/MINUTES
                                              queue)))

(defn too-many-requests-response
  "Returns a Ring response map with the HTTP status 429 (too many
   requests)."
  []
  (-> (response/response "too many requests")
      (response/header "Retry-After"
                       "1")
      (response/content-type "text/plain")
      (response/status 429)))

(defn ring-handler
  [request]
  (when (and (= (:request-method request)
                :post)
             (= (:uri request)
                "/eval"))
    (httpkit/as-channel
     request
     {:on-open
      (fn [channel]
        (try
          (.submit executor
                   (fn []
                     (try
                       (httpkit/send!
                        channel
                        (let [params (edn/read-string (slurp (:body request)))
                              result (eval-code params)]
                          {:status 200
                           :headers {"Content-Type" "application/edn"}
                           :body (pr-str result)}))
                       (catch Throwable e
                         (httpkit/send! channel
                                        (-> (str "internal server error\n\n"
                                                 "Stacktrace:\n"
                                                 (with-out-str (stacktrace/print-cause-trace e))
                                                 "\n"
                                                 "ex-data:\n"
                                                 (pr-str (ex-data e))
                                                 )
                                            (response/response)
                                            (response/content-type "text/plain")
                                            (response/status 500)))))))
          (catch java.util.concurrent.RejectedExecutionException _e
            ;; see also:
            ;; https://www.javamex.com/tutorials/threads/thread_pools_queues.shtml

            ;; `RejectedExecutionException` means all
            ;; Threads of the `executor` are busy and its
            ;; queue is also full. Therefore send the client
            ;; a 429 HTTP response (too many requests), so
            ;; that it sleeps and then retries the request:
            (httpkit/send! channel
                           (too-many-requests-response)))))
      :on-close (fn [ch status]
                  (httpkit/close ch))})))
