(ns studyflow.login.main
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [not-found]]
            [crypto.password.bcrypt :as bcrypt]
            [environ.core :refer [env]]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.element :as element]
            [hiccup.form :as form]
            [ring.util.response :as response]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [taoensso.carmine :as car :refer (wcar)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; View

(def app-title "Studyflow")
(def studyflow-env (keyword (env :studyflow-env)))
(def publishing-url (studyflow-env (env :publishing-url)))
(def cookie-domain (studyflow-env (env :cookie-domain)))
(def session-max-age (studyflow-env (env :session-max-age)))

(defn layout [title & body]
  (html5
   [:head
    [:title (str/join " - " [app-title title])]
    (include-css "screen.css")]
   [:body
    [:h1 title]
    [:div
      (element/link-to "/" "home")
      (element/link-to "/logout" "logout")]
    body]))

(defn login [msg email password]
  (form/form-to [:post "/"]
    (form/hidden-field "__anti-forgery-token" *anti-forgery-token*)
    [:div
      [:p msg]
      [:div
        (form/label "email" "email")
        (form/email-field "email" email)]
      [:div
        (form/label "password" "password")
        (form/password-field "password" password)]
      [:div
        (form/submit-button "login")]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Model

(def server1-conn  {:pool {} :spec {}})

(defmacro wcar*  [& body] `(car/wcar server1-conn ~@body))

(defn logged-in-users []
  (wcar* (car/keys "*")))

(defn encrypt [password]
  (bcrypt/encrypt password))

(defn create-user [db role email password]
  (sql/insert! db :users [:uuid :role :email :password]  [(str (java.util.UUID/randomUUID)) role email (encrypt password)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-user-by-email [db email]
  (first (sql/query db ["SELECT uuid, role, password FROM users WHERE email = ?" email])))

(defn find-user-by-uuid [db uuid]
  (first (sql/query db ["SELECT uuid, role FROM users WHERE uuid = ?" uuid])) )

(defn authenticate [user password]
  (bcrypt/check password (:password user)))

(defn expire-session [uuid]
  (wcar* (car/del uuid)))

(defn set-session [uuid role]
  (wcar* (car/set uuid role) (car/expire uuid session-max-age)))

(defn logged-in? [uuid]
  (= (wcar* (car/exists uuid)) 1))

(defn redirect-to [path]
  {:status  302
   :headers {"Location" path}})

(defn default-redirect-path [role]
  (case role
    "editor" publishing-url
    "tester" "https://staging.studyflow.nl"
    "/"))

(defn redirect-user [cookie-path role]
  (if cookie-path
    (redirect-to cookie-path) 
    (redirect-to (default-redirect-path role))))

(defn get-redirect-cookie [cookies]
  (:value (cookies "studyflow_redir_to")))

(defn get-uuid-from-cookie [cookies]
  (:value (get cookies "studyflow_session")))

(defn get-login-cookie [uuid]
  (if cookie-domain
    {:studyflow_session {:value uuid :domain cookie-domain :max-age session-max-age}}
    {:studyflow_session {:value uuid :max-age session-max-age}}))

(defn get-authenticated-response [cookies role]
  (redirect-user (get-redirect-cookie cookies) role))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Controller

(defroutes actions

  (GET "/" {db :db logged-in? :logged-in? cookies :cookies params :params}
    (if logged-in?
      (let [uuid (get-uuid-from-cookie cookies)]
        (get-authenticated-response cookies (:role (find-user-by-uuid db uuid)))) 
      (layout "login" (login (:msg params) (:email params) (:password params)))))


  (POST "/" {db :db cookies :cookies {:keys [email password]} :params}
    (if-let [user (find-user-by-email db email)] 
      (let [uuid (:uuid user) 
            role (:role user)] 
        (if (authenticate user password)
          (do
            (set-session uuid role) ; ! function
            (assoc (get-authenticated-response cookies user) :cookies (get-login-cookie uuid)))
          (layout "login" (login "wrong email / password combination" email password)))) 
      (layout "login"  (login "wrong email combination" email password))))

  (GET "/logout" {cookies :cookies}
    (expire-session (get-uuid-from-cookie cookies))
    (assoc (redirect-to "/") :cookies {:studyflow_session {:value "" :max-age -1}}))

  (not-found "Nothing here"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Wiring

(defn wrap-db [app db]
  (fn [req]
    (app (assoc req :db db))))

(defn wrap-login-state [app]
  (fn [req]
    (let [login-state (logged-in? (get-uuid-from-cookie (:cookies req)))]
      (app (assoc req :logged-in? login-state)))))

(def db
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (env :db-subname) 
   :user (env :db-user) 
   :password (env :db-password)})

(defn count-users  [db]
  (:count (first (sql/query db "SELECT COUNT(*) FROM users"))))

(defn set-studyflow-site-defaults []
  (-> site-defaults
    (assoc-in  [:session :cookie-name] "studyflow_login_session")))

(def app
  (->
   actions
   wrap-login-state
   (wrap-defaults (set-studyflow-site-defaults))
   (wrap-db db)))
