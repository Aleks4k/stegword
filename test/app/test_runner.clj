;clj -A:test -M -m app.test-runner
(ns app.test-runner
  (:require [clojure.test :refer :all]
            app.validation-test))
(defn -main []
  (run-tests 'app.validation-test))