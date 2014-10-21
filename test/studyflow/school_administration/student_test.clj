(ns studyflow.school-administration.student-test
  (:require
   [studyflow.school-administration.student.events :as events]
   [studyflow.school-administration.department.events :as department]
   [studyflow.school-administration.school.events :as school]
   [studyflow.school-administration.student :as student]
   [rill.temp-store :refer [execute command-result=]]
   [rill.uuid :refer [new-id]]
   [clojure.test :refer [deftest testing is]]))

(def student-id (new-id))
(def school-id (new-id))
(def department-id (new-id))


(deftest test-commands
  (testing "create students"
    (is (command-result= [:ok [(events/created student-id "Joost")]]
                         (execute (student/create! student-id "Joost")
                                  []))))

  (testing "assigning departments"
    (is (command-result= [:ok [(events/department-changed student-id department-id)]]
                         (execute (student/change-department! student-id 0 department-id)
                                  [(school/created school-id "SCHOOL" "123T")
                                   (department/created department-id school-id "DEPT")
                                   (events/created student-id "Pietje Puk")]))))

  (testing "assigning classes"
    (testing "without a department"
      (is (= :rejected
             (first (execute (student/change-class! student-id 0 "CLASS_NAME")
                             [(events/created student-id "Pietje Puk")])))))

    (testing "with a department"
      (is (command-result= [:ok [(events/class-assigned student-id department-id "CLASS_NAME")]]
                           (execute (student/change-class! student-id 1 "CLASS_NAME")
                                    [(school/created school-id "SCHOOL" "123T")
                                     (department/created department-id school-id "DEPT")
                                     (events/created student-id "Pietje Puk")
                                     (events/department-changed student-id department-id)]))))))

(deftest test-validate-with
  (testing "without errors"
    (is (= {} (student/validate-with (constantly true) "error" {} [:a]))))
  (testing "with errors"
    (is (= {:a ["error"]} (student/validate-with (constantly false) "error" {} [:a]))))
  (testing "with pre existing errors"
    (is (= {:a ["first" "second"]} (student/validate-with (constantly false) "second" {} [:a] {:a ["first"]})))))