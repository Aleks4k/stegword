(ns app.validation-test
  (:require [clojure.test :as t]
            [app.validation :as v]))
(t/deftest valid-password-test?
  (t/testing "Tačni passwordi"
    (t/is (v/valid-password? "Password1"))
    (t/is (v/valid-password? "abc12345"))
    (t/is (v/valid-password? "Test1234")))
  (t/testing "Netačni passwordi"
    (t/is (not (v/valid-password? "short1")))      ; manje od 8
    (t/is (not (v/valid-password? "onlyletters"))) ; nema cifru
    (t/is (not (v/valid-password? "12345678")))    ; nema slovo
    (t/is (not (v/valid-password? nil)))           ; nije string
    (t/is (not (v/valid-password? 12345678)))))    ; nije string
(t/deftest valid-png-file?
  (t/testing "Pravilni PNG fajlovi"
    (t/is (v/png-file? "test/images/image.png"))
    )
  (t/testing "Nepravilni PNG fajlovi"
    (t/is (not (v/png-file? "test/images/image.jpg")))
    (t/is (not (v/png-file? "test/images/jpgImageSavedAsPNG.png")))
    (t/is (not (v/png-file? "test/images/unknown.jpg")))
    )
  )