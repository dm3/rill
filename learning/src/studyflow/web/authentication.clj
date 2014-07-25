(ns studyflow.web.authentication
  (:require [clojure.tools.logging :as log]
            [ring.middleware.cookies :as cookies]
            [studyflow.learning.read-model :as read-model]
            [studyflow.components.session-store :as session-store]))

(defn redirect-login [req]
  {:status 302
   :headers {"Location" "http://localhost:4000/"}
   :cookies {"studyflow_redir_to" {:value (str "http://localhost:3000" (get req :uri))
                                   :path "/"}
             "studyflow_session" {:value ""
                                  :path "/"
                                  :max-age -1}}
   :body nil})

(defn wrap-student [handler read-model]
  (fn [req]
    (if-let [student-id (get req :student-id)]
      (if-let [student (read-model/get-student @read-model student-id)]
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

(defn wrap-check-cookie [handler]
  (fn [req]
    (if-let [session-id (get-in req [:cookies "studyflow_session" :value])]
      (handler (assoc req :session-id session-id))
      (redirect-login req))))

(defn wrap-authentication [handler read-model session-store]
  (-> handler
      (wrap-student read-model)
      (wrap-student-id session-store)
      wrap-check-cookie
      cookies/wrap-cookies))
