(ns app.validation-test
  (:require [clojure.test :as t]
            [app.validation :as v]))
(t/deftest valid-password-test?
  (t/testing "Correct passwords"
    (t/is (v/valid-password? "Password1"))
    (t/is (v/valid-password? "abc12345"))
    (t/is (v/valid-password? "Test1234")))
  (t/testing "Incorrect passwords"
    (t/is (not (v/valid-password? "short1")))      ; less than 8
    (t/is (not (v/valid-password? "onlyletters"))) ; no digit
    (t/is (not (v/valid-password? "12345678")))    ; no letter
    (t/is (not (v/valid-password? nil)))           ; not string
    (t/is (not (v/valid-password? 12345678)))))    ; not string
(t/deftest valid-png-file?
  (t/testing "Correct PNG files"
    (t/is (v/png-file? "test/images/image.png"))
    )
  (t/testing "Incorrect PNG files"
    (t/is (not (v/png-file? "test/images/image.jpg")))
    (t/is (not (v/png-file? "test/images/jpgImageSavedAsPNG.png")))
    (t/is (not (v/png-file? "test/images/unknown.jpg")))
    )
  )