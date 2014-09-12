(ns studyflow.teaching.web.reports.export
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hiccup.core :refer [h]]
            [clj-time.local :as time]
            [clj-time.format :as format-time]
            [studyflow.teaching.web.util :refer [completion-percentage]]
            [dk.ative.docjure.spreadsheet :as excel]
            [ring.util.io :refer [piped-input-stream]]))

(defn- local-time []
  (format-time/unparse (format-time/formatter-local "dd-MM-yyyy HH:mm:ss") (time/local-now)))

(defn- local-date []
  (time/format-local-time (time/local-now) :date))

(defn- completion-export [{:keys [finished total] :as completion}]
  (if (and completion (> total 0))
    [finished (completion-percentage completion)]
    ["-" "-"]))

(defn sheet-content-for-criterion [criterion class students domains]
  (let [meta-data ["Studyflow report"
                   (str  "Klas: " (:class-name class))
                   (str  "Meijerink: " criterion)
                   (str  "Date: " (local-time))]
        sup-header (into [""]
                         (interleave (into ["Totaal"] domains)
                                     (repeat "")))
        domains-all (into [:all]
                          domains)
        domains-total (apply hash-map
                             (interleave domains-all
                                         (map (fn [x] (:total  (get-in (first students) [:completion criterion x])))
                                              domains-all)))
        header (reduce into
                       ["Leerling naam"]
                       (map (fn [x]
                              [(str "# Section finished (max " (get domains-total x) ")")
                               "Percentage finished"])
                            domains-all))
        student-data (map (fn [student] (reduce into [(h (:full-name student))]
                                                (map (fn [domain]
                                                       (completion-export (get-in student [:completion criterion domain])))
                                                     domains-all)))
                          (sort-by :full-name students))
        class-data (reduce into ["Klassengemiddelde"]
                           (map (fn [domain]
                                  (completion-export (get-in class [:completion criterion domain])))
                                (into [:all] domains)))]
    (-> [meta-data]
        (conj sup-header)
        (conj header)
        (into student-data)
        (conj class-data))))



(defn decorate-sheet [sheet-title workbook column-numbers]
  (let [sheet (excel/select-sheet sheet-title workbook)]
    (.setColumnWidth sheet 0 8000)
    (doseq [col (range 0 column-numbers)]
      (.setColumnWidth sheet
                       (inc col)
                       6000))
    (excel/set-cell-style! (first (excel/cell-seq (first (excel/row-seq sheet))))
                           (excel/create-cell-style! workbook {:font {:bold true}}))
    (excel/set-row-style! (second (excel/row-seq sheet))
                          (excel/create-cell-style! workbook {:background :grey_25_percent
                                                              :font {:bold true}}))
    (excel/set-row-style! (last (excel/row-seq sheet))
                          (excel/create-cell-style! workbook {:background :light_cornflower_blue
                                                              :font {:bold true}}))))

(defn render-export [classes domains students meijerink-criteria params]
  (let [class (first (filter #(= (:class-id params) (:id %)) classes))
        file-name (str "studyflow-export-" (:class-name class) "-" (local-date))
        workbook (reduce (fn [wb criterion]
                           (let [sheet (excel/add-sheet! wb criterion)
                                 data (sheet-content-for-criterion criterion
                                                                   class
                                                                   students
                                                                   domains)]
                             (excel/add-rows! sheet data)
                             wb))
                         (excel/create-workbook (first meijerink-criteria)
                                                (sheet-content-for-criterion (first meijerink-criteria)
                                                                             class
                                                                             students
                                                                             domains))
                         (rest meijerink-criteria))

        sheet (excel/select-sheet (first meijerink-criteria) workbook)]
    (doseq [criterion meijerink-criteria]
      (decorate-sheet criterion workbook (* 2 (inc (count domains)))))
    {:status 200
     :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
               "Content-Disposition" (str "attachment; filename=\"" file-name "\".xslx" )
               }
     :body (piped-input-stream
            (fn [out]
              (.write workbook out)))}))
