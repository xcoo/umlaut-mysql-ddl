(ns ddl-test
  (:require [clojure.test :refer :all]
            [clojure.test :as t]
            [umlaut.generators.ddl.mysql :as mysql]))

(deftest ddl-generate-test
  (is (= (->> (mysql/gen "datamodel.umlaut")
              (map :create)
              (clojure.string/join "\n\n"))
         (slurp "fixture.sql"))))

(comment
  (->> (mysql/gen "datamodel.umlaut")
       (map :create)
       (clojure.string/join "\n\n")
       (spit "fixture.sql"))
  )
