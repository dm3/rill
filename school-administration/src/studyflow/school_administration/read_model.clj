(ns studyflow.school-administration.read-model)

(defn list-students
  "sequence of all the students in the system"
  [model]
  (sort-by :full-name (vals (:students model))))

(defn set-student
  [model id student]
  (assoc-in model [:students id] student))

(defn set-student-full-name
  [model id name]
  (assoc-in model [:students id :full-name] name))




