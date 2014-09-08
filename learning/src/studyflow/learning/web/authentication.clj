(ns studyflow.learning.web.authentication
  (:require [clojure.tools.logging :as log]
            [ring.middleware.cookies :as cookies]
            [studyflow.web.authentication :refer [redirect-login wrap-check-cookie]]
            [studyflow.learning.read-model :as read-model]
            [studyflow.components.session-store :as session-store]))

(defn wrap-student [handler]
  (fn [{:keys [read-model] :as req}]
    (if-let [student-id (get req :student-id)]
      (if-let [student (read-model/get-student read-model student-id)]
        (handler (assoc req :student (assoc student :student-id student-id)))
        (do
          (log/warn "Can't find student through session, perhaps re-logging in will work")
          (redirect-login req)))
      req)))

(defn get-student-id [session-store session-id]
  (when-let [user-id (session-store/get-user-id session-store session-id)]
    (when (= (session-store/get-role session-store session-id) "student")
      user-id)))

(defn wrap-student-id [handler session-store]
  (fn [req]
    (if-let [session-id (get req :session-id)]
      (if-let [student-id (get-student-id session-store session-id)]
        (handler (-> req
                     (assoc :student-id student-id)
                     (dissoc :session-id)))
        ;; session expired
        (redirect-login req))
      req)))

(defn wrap-authentication [handler session-store]
  (-> handler
      wrap-student
      (wrap-student-id session-store)
      (wrap-check-cookie)
      cookies/wrap-cookies))