(ns app.test-runner
  (:require [clojure.test :as t]
            app.validation-test
            app.crypto-test
            app.image-test
            app.stego-test
            app.actions-test))
(defn -main []
  (t/run-tests 'app.validation-test
               'app.crypto-test
               'app.image-test
               'app.stego-test
               'app.actions-test))