(ns rill.handler
  (:require [rill.aggregate :as aggregate]
            [rill.event-store :as store]
            [rill.event-stream :as stream]
            [rill.message :as msg :refer [->type-keyword]]
            [clojure.tools.logging :as log]))

(defmulti aggregate-ids
  "given a command, return the ids of the aggregates that should be
  fetched before calling handle-command. The first aggregate should be
  the target of the events generated by handle-command."
  (fn [command]
    (msg/type command)))

(defmacro defaggregate-ids [klass & ks]
  {:pre [(every? #(and (symbol? %)
                       (not (keyword? %)))
                 ks)]}
  `(defmethod aggregate-ids ~(->type-keyword klass)
     [command#]
     (map (partial get command#) ~(mapv keyword ks))))

(defn target-aggregate-id
  [command]
  (first (aggregate-ids command)))

(defn valid-commit?
  [[event & events]]
  ;; Every event must apply to the same aggregate root
  (and event
       (let [id (msg/stream-id event)]
         (every? #(= id (msg/stream-id %)) events))))

(defn validate-commit
  [events]
  (when-not (valid-commit? events)
    (throw (Exception. "Transactions must apply to exactly one aggregate"))))

(defn commit-events
  [store stream-id from-version events]
  (validate-commit events)
  (log/info ["committing events" events])
  (let [stream-id-from-event (msg/stream-id (first events))]
    (if (= stream-id stream-id-from-event)
                                        ; events apply to current aggregate
      (store/append-events store stream-id from-version events)
                                        ; events apply to newly created aggregate
      (store/append-events store stream-id-from-event stream/empty-stream-version events))))

(defmulti handle-command
  "handle command given aggregates. returns a seq of events"
  (fn [command & aggregates]
    (msg/type command)))

(defn update-aggregate-and-version
  [aggregate version events]
  (reduce (fn [[aggregate version] event]
            [(aggregate/handle-event aggregate event) (inc version)])
          [aggregate version]
          events))

(defn load-aggregate-and-version
  [events]
  (update-aggregate-and-version nil stream/empty-stream-version events))

(defn prepare-aggregates
  "fetch the primary event stream id and version and aggregates for `command`"
  [event-store command]
  (let [[id & additional-ids] (aggregate-ids command)
        [aggregate current-version] (load-aggregate-and-version (store/retrieve-events event-store id))
        additional-aggregates (map #(aggregate/load-aggregate (store/retrieve-events event-store %)) additional-ids)]
    (into [id current-version aggregate] additional-aggregates)))

(defn try-command
  [event-store command]
  (let [[id version & aggregates] (prepare-aggregates event-store command)]
    (log/debug [:try-command command])
    (let [result (if-let [events (apply handle-command command aggregates)]
                   (if (commit-events event-store id version events)
                     :ok
                     :conflict)
                   :rejected)]
      (log/debug [result])
      result)))

(defn make-handler [event-store]
  (fn [command]
    (try-command event-store command)))
