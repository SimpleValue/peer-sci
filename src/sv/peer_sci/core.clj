(ns sv.peer-sci.core
  (:require [sci.core :as sci]
            [clojure.edn :as edn]))

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

(defn ring-handler
  [request]
  (when (and (= (:request-method request)
                :post)
             (= (:uri request)
                "/eval"))
    (let [params (edn/read-string (slurp (:body request)))
          result (eval-code params)]
      {:status 200
       :headers {"Content-Type" "application/edn"}
       :body (pr-str result)})))
