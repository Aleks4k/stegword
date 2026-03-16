(ns app.image-test
  (:require [clojure.test :as t]
            [app.image :as image]
            [clojure.java.io :as io])
  (:import (java.awt.image BufferedImage)))
(def test-image-path "test/images/image.png")
(t/deftest calculate-image-capacity-in-bits-test
  (let [img (image/load-image-as-rgb test-image-path)
        expected-capacity (* (.getWidth img) (.getHeight img) 3)]
    (t/is (= expected-capacity
             (image/calculate-image-capacity-in-bits test-image-path)))))
(t/deftest build-encrypted-output-path-test
  (t/is (= (.getPath (clojure.java.io/file "test/images/image_encrypted.png"))
           (image/build-encrypted-output-path "test/images/image.png"))))
(t/deftest load-image-as-rgb-test
  (let [img (image/load-image-as-rgb test-image-path)]
    (t/is (instance? BufferedImage img))
    (t/is (= BufferedImage/TYPE_INT_RGB (.getType img)))))
(t/deftest get-image-pixels-length-test
  (let [img (image/load-image-as-rgb test-image-path)
        pixels (image/get-image-pixels img)]
    (t/is (= (* (.getWidth img) (.getHeight img))
             (alength pixels)))))