(ns app.test-runner
  (:require [clojure.test :as t]
            app.validation-test
            app.crypto-test
            app.image-test
            app.stego-test
            app.actions-test))
(defn -main []
  (let [results (t/run-tests 'app.validation-test
                             'app.crypto-test
                             'app.image-test
                             'app.stego-test
                             'app.actions-test)
        failures (+ (:fail results) (:error results))]
    (if (> failures 0)
      (do
        (println "\nTESTS FAILED!")
        (System/exit 1))
      (do
        (println "\nAll tests passed.")
        (System/exit 0)))))