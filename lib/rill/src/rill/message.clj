(ns rill.message
  (:refer-clojure :exclude [type])
  (:require [schema.macros :as sm]
            [schema.core :as s]
            [rill.uuid :refer [new-id]]
            [nl.zeekat.identifiers :refer [lisp-name]]))

(def id ::id)
(def type ::type)
(def number ::number)

(defn data
  [m]
  (dissoc m id type number))

(defmulti strict-map->Message
  (fn [s m]
    s))

(defn ->type-keyword
  [ns sym]
  (keyword (name (ns-name ns)) (name sym)))

(defn params->args
  [params]
  (mapv #(symbol (name (first %)))
        (partition-all 2 params)))

(defmulti primary-aggregate-id
  "The id of the aggregate that will handle this message"
  ::type)

(defmacro defmessage
  [name & params]
  {:pre [(every? keyword? (take-nth 2 (butlast params)))]}
  (let [type-keyword (->type-keyword (ns-name *ns*) name)
        name-str (clojure.core/name name)
        primary-aggregate-id-fn (if (even? (count params))
                                  (keyword (first params))
                                  (last params))
        params (if (even? (count params))
                 params
                 (butlast params))]
    `(do (def ~name ~(into {::id s/Uuid
                            ::type type-keyword}
                           (map vec (partition 2 params))))

         (defn ~(symbol (str "map->" name-str))
           ~(str "Inserts a " name-str " rill.message/type tag into the given map.")
           [params#]
           (assoc params# ::type ~type-keyword))

         ~(let [args (params->args params)
                ks (mapv keyword args)
                id-arg (gensym "msg_id_")]
            `(do (defn ~(symbol (str "->" name-str))
                   ~(str "Constructs a " name-str " message from the positional arguments.")
                   ~(vec (into [id-arg] args))
                   ~(into {::id id-arg
                           ::type type-keyword}
                          (zipmap ks args)))
                 (defn ~(symbol (lisp-name name-str))
                   ~(str "Create a new " name-str " message from the positional arguments. Automatically generates a new message id.")
                   ~(vec args)
                   ~(into {::id `(new-id)
                           ::type type-keyword}
                          (zipmap ks args)))))

         (defmethod primary-aggregate-id
           ~type-keyword
           [message#]
           (~primary-aggregate-id-fn message#)))))

(defmacro defcommand
  [name & params]
  `(defmessage ~name ~@params))

(defmacro defevent
  [name & params]
  `(defmessage ~name ~@params))
