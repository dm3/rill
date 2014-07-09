(ns studyflow.web.service
  (:require [ajax.core :refer [GET POST PUT] :as ajax-core]
            [cljs-uuid.core :as uuid]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [studyflow.web.json-edn :as json-edn]
            [goog.string :as gstring]
            [studyflow.web.aggregates :as aggregates]
            [cljs.core.async :refer [<!] :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn try-command [cursor command]
  (prn :try-command command)
  (let [[command-type & args] command]
    (condp = command-type
      "section-test-commands/init"
      (let [[section-id] args
            course-id (get-in @cursor [:static :course-id])
            section-test-id (str "student-idDEFAULT_STUDENT_IDsection-id" section-id)]
        (PUT (str "/api/section-test-init/" course-id "/" section-id "/" section-test-id)
             {:format :json
              :handler (fn [res]
                         (let [events (:events (json-edn/json->edn res))]
                           (om/transact! cursor
                                         [:aggregates section-test-id]
                                         (fn [agg]
                                           (prn "playing events on: " agg " with aggr-id " section-test-id " with events: " events)
                                           (let [res (aggregates/apply-events agg events)]
                                             (prn "RES: " res)
                                             res)))))
              }))

      "section-test-commands/check-answer"
      (let [[section-test-id section-id course-id question-id inputs] args]
        (PUT (str "/api/section-test-check-answer/" section-test-id "/"  section-id "/" course-id "/" question-id)
             {:params inputs
              :format :json
              :handler (fn [res]
                         (let [events (:events (json-edn/json->edn res))]
                           (om/transact! cursor
                                         [:aggregates section-test-id]
                                         (fn [agg]
                                           (prn "playing events on: " agg " with aggr-id " section-test-id " with events: " events)
                                           (let [res (aggregates/apply-events agg events)]
                                             (prn "RES: " res)
                                             res)))))}))
      "section-test-commands/next-question"
      (let [[section-test-id] args]
        (let [[section-test-id section-id course-id] args]
          (PUT (str "/api/section-test-next-question/" section-test-id "/"  section-id "/" course-id)
               {:format :json
                :handler (fn [res]
                           (let [events (:events (json-edn/json->edn res))]
                             (om/transact! cursor
                                           [:aggregates section-test-id]
                                           (fn [agg]
                                             (prn "playing events on: " agg " with aggr-id " section-test-id " with events: " events)
                                             (let [res (aggregates/apply-events agg events)]
                                               (prn "RES: " res)
                                               res)))))})))
      nil)))

;; a bit silly to use an Om component for something that is not UI,
;; but don't know how to participate in state managament otherwise
;; root component for services seems to be the default om pattern:
;; https://groups.google.com/forum/#!topic/clojurescript/DHJvcGey8Sc

(defn wrap-service [widgets]
  (fn [cursor owner]
    (reify
      om/IWillMount
      (will-mount [_]
        (println "service will mount")
        ;; listen to server push connection here
        (let [command-channel (om/get-shared owner :command-channel)]
            (go (loop []
               (when-let [command (<! command-channel)]
                 (try-command cursor command)
                 (recur)))))


        ;; initialize the menu
        (GET (str "/api/course-material/"
                  (get-in cursor [:static :course-id]))
             {:params {}
              :handler (fn [res]
                         (println "Service heard: " res)
                         (let [course-data (json-edn/json->edn res)]
                           (om/update! cursor
                                       [:view :course-material] course-data)))
              :error-handler (fn [res]
                               (println "Error handler" res)
                               (println res))}))
      om/IRender
      (render [_]
        (om/build widgets cursor)))))

(defn find-event [name events]
  (first (filter #(gstring/endsWith (:type %) name) events)))



(defn listen [tx-report cursor]
  (let [{:keys [path new-state]} tx-report]
    (cond
     (= path [:view :selected-path])
     (if-let [{:keys [chapter-id section-id]} (get-in new-state path)]
       (if-let [section-data (get-in new-state [:view :section section-id :data])]
         nil ;; data already loaded
         (do
           (let [section-test-id (str "student-idDEFAULT_STUDENT_IDsection-id" section-id)]
             (prn "Load aggregate: " section-test-id)
             (when-not (contains? (get-in new-state [:aggregates section-test-id]))
               (GET (str "/api/section-test-replay/" section-test-id)
                    {:format :json
                     :handler (fn [res]
                                (let [events (:events (json-edn/json->edn res))]
                                  (om/transact! cursor
                                                [:aggregates section-test-id]
                                                (fn [agg]
                                                  (prn "playing events on: " agg " with aggr-id " section-test-id " with events: " events)
                                                  (let [res (aggregates/apply-events agg events)]
                                                    (prn "RES: " res)
                                                    res)))))})))
           (GET (str "/api/course-material/"
                     (get-in new-state [:static :course-id])
                     "/chapter/" chapter-id
                     "/section/" section-id)
               {:params {}
                :handler (fn [res]
                           (println "Service heard: " res)
                           (let [section-data (json-edn/json->edn res)]
                             (println "section: " section-data)
                             (om/transact! cursor
                                           #(assoc-in %
                                                      [:view :section (:id section-data) :data]
                                                      section-data))))
                :error-handler (fn [res]
                                 (println "Error handler" res)
                                 (println res))})))
       nil)

     (let [[view section _ test _] path]
       (and (= view :view)
            (= section :section)
            (= test :test)))
     (let [[_ _ section-id _ question-id] path
           chapter-id (get-in new-state [:view :selected-path :chapter-id])]
       (GET (str "/api/course-material/"
                 (get-in new-state [:static :course-id])
                 "/chapter/" chapter-id
                 "/section/" section-id
                 "/question/" question-id)
            {:params {}
             :handler (fn [res]
                        (let [question-data (json-edn/json->edn res)]
                          (om/transact! cursor
                                        #(assoc-in %
                                                   [:view :section section-id :test (:id question-data)]
                                                   question-data))))
             :error-handler (fn [res]
                              (println "Error handler" res)
                              (println res))}))

     :else nil)))
