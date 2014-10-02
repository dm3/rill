(ns studyflow.teaching.web.reports.query
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [hiccup.core :refer [h]]
            [hiccup.form :as form]
            [studyflow.teaching.read-model :as read-model]
            [studyflow.teaching.web.reports.export :refer [render-export]]
            [studyflow.teaching.web.util :refer :all]
            [rill.uuid :refer [uuid]]
            [ring.util.response :refer [redirect-after-post]]))

(defn drop-list-classes [classes current-meijerink report-name]
  [:div.m-select-box.show
   [:ul.dropdown
    (map (fn [class]
           [:li.dropdown-list-item
            [:a.dropdown-link {:href
                               (if current-meijerink
                                 (str "/reports/" (:id class) "/" current-meijerink "/" report-name)
                                 (str "/reports/" (:id class) "/" report-name))}
             (:full-name class)]])
         classes)]])

(defn drop-list-meijerink [class meijerink-criteria report-name]
  [:div.m-select-box.show
   [:ul.dropdown
    (map (fn [meijerink]
           [:li.dropdown-list-item
            [:a.dropdown-link {:href (str "/reports/" (:id class) "/" meijerink "/" report-name)}
             meijerink]])
         meijerink-criteria)]])

(defn render-completion [class scope students classes meijerink-criteria domains params options]
  (let [meijerink-criteria (sort meijerink-criteria)
        domains (sort domains)
        report-name "completion"
        scope (if (str/blank? scope) nil scope)]
    (layout
     (merge {:title (if class
                      (str "Overzicht voor \"" (:full-name class) "\"")
                      "Overzicht")}
            options)

     (drop-list-classes classes scope report-name)
     (when class
       (drop-list-meijerink class meijerink-criteria report-name))

     (when students
       [:div
        [:table.students
         [:thead
          [:th.full-name]
          [:th.completion.number "Tijd"]
          [:th.completion.number "Totaal"]
          (map (fn [domain]
                 [:th.domain.number (h domain)])
               domains)]
         [:tbody
          (map (fn [student]
                 [:tr.student {:id (str "student-" (:id student))}
                  [:td.full-name
                   (h (:full-name student))]
                  [:td.completion.number.time-spent
                   (time-spent-html (get-in student [:time-spent scope]))]
                  (map (fn [domain]
                         [:td.completion.number {:class (classerize domain)}
                          (completion-html (get-in student [:completion scope domain]))])
                       (into [:all] domains))])
               (sort-by :full-name students))]
         [:tfoot
          [:th.average "Klassengemiddelde"]
          [:td.average.number.time-spent
           (time-spent-html (get-in class [:time-spent scope]))]
          (map (fn [domain]
                 [:td.average.number {:class (classerize domain)}
                  (completion-html (get-in class [:completion scope domain]))])
               (into [:all] domains))]]
        [:a {:href (str "/reports/export?class-id=" (:class-id params)) :target "_blank"} "Exporteren naar Excel"]]))))

(defn render-chapter-list [class classes chapter-list params options]
  (let [selected-chapter-id (uuid (:chapter-id params))
        selected-section-id (uuid (:section-id params))
        class-id (:class-id params)
        report-name "chapter-list"]
    (layout
     (merge {:title (if class
                      (str "Hoofdstukken voor \"" (:full-name class) "\"")
                      "Hoofdstukken")}
            options)

     (drop-list-classes classes nil report-name)

     (when class
       [:div#m-teacher_chapter_list
        [:nav#teacher_chapter_list_sidenav
         [:ol.chapter-list
          (for [{chapter-title :title chapter-id :id :as chapter} (:chapters chapter-list)]
            [:li {:class (str "chapter" (when (= selected-chapter-id (str chapter-id)) " open"))}
             [:a.chapter-title
              {:href (str "/reports/" (:id class) "/chapter-list/" chapter-id)}
              (h chapter-title)
              (completion-html (get-in chapter-list [:chapters-completion chapter-id]))]
             (when (= selected-chapter-id chapter-id)
               [:ol.section-list
                (for [{section-title :title section-id :id :as section} (:sections chapter)]
                  [:li {:class (str "section" (when (= selected-section-id section-id) " selected"))}
                   [:a.section_link
                    {:href (str "/reports/" (:id class) "/chapter-list/" chapter-id "/" section-id)}
                    (h section-title)]

                   (when-let [section-counts (get-in chapter-list [:sections-total-status chapter-id section-id])]
                     [:div.section_status
                      (for [status [:stuck :in-progress :unstarted :finished]]
                        [:span {:class (name status)} (get section-counts status 0)])])])])])]]

        (when-let [section-counts (get-in chapter-list [:sections-total-status selected-chapter-id selected-section-id])]
          [:div.teacher_chapter_list_main
           (for [status [:stuck :in-progress :unstarted :finished]]
             (for [student (sort-by :full-name (get-in section-counts [:student-list status]))]
               [:div.student {:class (name status)} (:full-name student)
                [:span.time-spent (time-spent-html (:time-spent student))]]))])]))))

(defroutes app
  (GET "/reports/"
       {}
       (redirect-after-post "/reports/completion"))

  (GET "/reports/completion"
       {:keys [read-model flash teacher redirect-urls]}
       (let [classes (read-model/classes read-model teacher)
             meijerink-criteria (read-model/meijerink-criteria read-model)
             options (assoc flash :redirect-urls redirect-urls)]
         (render-completion nil nil nil classes meijerink-criteria nil nil options)))

  (GET "/reports/:class-id/completion"
       {:keys [read-model flash teacher redirect-urls]
        {:keys [class-id] :as params} :params}
       (let [classes (read-model/classes read-model teacher)
             meijerink-criteria (read-model/meijerink-criteria read-model)
             domains (read-model/domains read-model)
             class (some (fn [class]
                           (when (= class-id (:id class))
                             (read-model/decorate-class-completion read-model class))) classes)
             students (when class
                        (->> (read-model/students-for-class read-model class)
                             (map (partial read-model/decorate-student-completion read-model))))
             options (assoc flash :redirect-urls redirect-urls)]
         (binding [*current-nav-uri* "/reports/completion"]
           (render-completion class nil students classes meijerink-criteria domains params options))))

  (GET "/reports/:class-id/:meijerink/completion"
       {:keys [read-model flash teacher redirect-urls]
        {:keys [class-id meijerink] :as params} :params}
       (let [classes (read-model/classes read-model teacher)
             meijerink-criteria (read-model/meijerink-criteria read-model)
             domains (read-model/domains read-model)
             class (some (fn [class]
                           (when (= class-id (:id class))
                             class)) classes)
             students (when class
                        (->> (read-model/students-for-class read-model class)
                             (map (comp (partial read-model/decorate-student-completion read-model)
                                        (partial read-model/decorate-student-time-spent read-model)))))
             class (if students
                     (->> class
                          (read-model/decorate-class-completion read-model students)
                          (read-model/decorate-class-time-spent read-model students))
                     class)
             options (assoc flash :redirect-urls redirect-urls)]
         (binding [*current-nav-uri* "/reports/completion"]
           (render-completion class meijerink students classes meijerink-criteria domains params options))))

  (GET "/reports/chapter-list"
       {:keys [read-model flash teacher redirect-urls]}
       (let [classes (read-model/classes read-model teacher)
             options (assoc flash :redirect-urls redirect-urls)]
         (binding [*current-nav-uri* "/reports/chapter-list"]
           (render-chapter-list nil classes nil nil options))))

  (GET "/reports/:class-id/chapter-list"
       {:keys [read-model flash teacher redirect-urls]
        {:keys [class-id] :as params} :params}
       (let [classes (read-model/classes read-model teacher)
             class (some (fn [c] (when (= class-id (:id c)) c)) classes)
             chapter-list (when class (read-model/chapter-list read-model class nil nil))
             options (assoc flash :redirect-urls redirect-urls)]
         (binding [*current-nav-uri* "/reports/chapter-list"]
           (render-chapter-list class classes chapter-list params options))))

  (GET "/reports/:class-id/chapter-list/:chapter-id"
       {:keys [read-model flash teacher redirect-urls]
        {:keys [class-id chapter-id] :as params} :params}
       (let [chapter-id (uuid chapter-id)
             classes (read-model/classes read-model teacher)
             class (some (fn [c] (when (= class-id (:id c)) c)) classes)
             chapter-list (when class (read-model/chapter-list read-model class chapter-id nil))
             options (assoc flash :redirect-urls redirect-urls)]
         (binding [*current-nav-uri* "/reports/chapter-list"]
           (render-chapter-list class classes chapter-list params options))))

  (GET "/reports/:class-id/chapter-list/:chapter-id/:section-id"
       {:keys [read-model flash teacher redirect-urls]
        {:keys [class-id chapter-id section-id] :as params} :params}
       (let [chapter-id (uuid chapter-id)
             section-id (uuid section-id)
             classes (read-model/classes read-model teacher)
             class (some (fn [c] (when (= class-id (:id c)) c)) classes)
             chapter-list (when class (read-model/chapter-list read-model class chapter-id section-id))
             options (assoc flash :redirect-urls redirect-urls)]
         (binding [*current-nav-uri* "/reports/chapter-list"]
           (render-chapter-list class classes chapter-list params options))))



  (GET "/reports/export"
       {:keys [read-model teacher]
        {:keys [class-id] :as params} :params}
       (let [classes (read-model/classes read-model teacher)
             domains (sort (read-model/domains read-model))
             meijerink-criteria (sort (read-model/meijerink-criteria read-model))
             class (some (fn [class]
                           (when (= class-id (:id class))
                             class)) classes)
             students (when class
                        (->> (read-model/students-for-class read-model class)
                             (map (comp (partial read-model/decorate-student-completion read-model)
                                        (partial read-model/decorate-student-time-spent read-model)))))
             class (if students
                     (->> class
                          (read-model/decorate-class-completion read-model students)
                          (read-model/decorate-class-time-spent read-model students))
                     class)]
         (render-export class students domains meijerink-criteria))))
