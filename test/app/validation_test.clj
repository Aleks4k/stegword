(ns app.validation-test
  (:require [clojure.test :refer :all]
            [app.validation :refer :all]))
(deftest valid-password-test?
  (testing "Tačni passwordi"
    (is (valid-password? "Password1"))
    (is (valid-password? "abc12345"))
    (is (valid-password? "Test1234")))
  (testing "Netačni passwordi"
    (is (not (valid-password? "short1")))      ; manje od 8
    (is (not (valid-password? "onlyletters"))) ; nema cifru
    (is (not (valid-password? "12345678")))    ; nema slovo
    (is (not (valid-password? nil)))           ; nije string
    (is (not (valid-password? 12345678)))))    ; nije string
(deftest valid-png-file?
  (testing "Pravilni PNG fajlovi"
    (is (png-file? "test/images/image.png"))
    )
  (testing "Nepravilni PNG fajlovi"
    (is (not (png-file? "test/images/image.jpg")))
    (is (not (png-file? "test/images/jpgImageSavedAsPNG.png")))
    (is (not (png-file? "test/images/unknown.jpg")))
    )
  )