(ns rill.event-store.memory
  "An in-memory event store for testing purposes"
  (:require [clojure.tools.logging :as log]
            [rill.event-store :as store]
            [rill.event-stream :as stream :refer [all-events-stream-id]]
            [rill.message :as message]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn with-cursors
  [c events]
  (map-indexed #(with-meta %2 {:cursor (+ c %1)}) events))

(deftype MemoryStore [state]
  store/EventStore
  (retrieve-events-since [this stream-id cursor wait-for-seconds]
    (let [cursor (if-let [c (:cursor (meta cursor))]
                   c
                   cursor)]
      (loop [wait wait-for-seconds]
        (let [substream (subvec (get @state stream-id stream/empty-stream) (inc cursor))]
          (if (empty? substream)
            (if (< 0 wait-for-seconds)
              (do (Thread/sleep 200)
                  (recur (dec wait)))
              (with-cursors (inc cursor) substream))
            (with-cursors (inc cursor) substream))))))

  (append-events [this stream-id from-version events]
    (try+ (swap! state (fn [old-state]
                         (let [current-stream (get old-state stream-id stream/empty-stream)
                               all-stream (get old-state all-events-stream-id stream/empty-stream)
                               current-version (dec (count current-stream))
                               events (map #(assoc %1 message/number (+ (if (= -2 from-version)
                                                                          current-version
                                                                          from-version) %2))
                                           events
                                           (iterate inc 1))]
                           (if (or (= -2 from-version)
                                   (= (dec (count current-stream)) from-version))
                             (-> old-state
                                 (assoc stream-id (into current-stream events))
                                 (assoc all-events-stream-id (into all-stream events)))
                             (throw+ ::out-of-date)))))
          true
          (catch #(= % ::out-of-date) err
            nil))))

(defn memory-store
  []
  (MemoryStore. (atom {})))
