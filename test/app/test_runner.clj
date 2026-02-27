;clj -A:test -M -m app.test-runner
(ns app.test-runner
  (:require [clojure.test :as t]
            app.validation-test))
(defn -main []
  (t/run-tests 'app.validation-test))