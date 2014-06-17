(ns studyflow.learning.read-model.event-listener
  (:require [studyflow.learning.read-model.event-handler :refer [handle-event]]
            [studyflow.loop-tools :refer [while-let]]
            [clojure.core.async :refer [<!! thread]]
            [clojure.tools.logging :as log]))

(defn listen!
  "listen on event-channel"
  [model-atom event-channel]
  (thread
    (while-let [e (<!! event-channel)]
               (log/info (pr-str e))
               (swap! model-atom handle-event e))))

