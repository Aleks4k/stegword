(ns app.stego-test
  (:require [clojure.test :as t]
            [app.stego :as stego]
            [app.crypto :as crypto])
  (:import (java.nio.charset StandardCharsets)))
(t/deftest build-and-parse-header-roundtrip-test
  (let [salt (byte-array (map byte (range 16)))
        header (stego/build-header-byte-array 0 true 12345 salt)
        parsed (stego/parse-header-byte-array header)]
    (t/is (= 0 (:version-number parsed)))
    (t/is (= true (:use-gzip? parsed)))
    (t/is (= 12345 (:encrypted-message-length-in-bytes parsed)))
    (t/is (= (vec salt) (vec (:salt-bytes parsed))))))
(t/deftest calculate-position-array-size-test
  (t/is (= 1 (stego/calculate-position-array-size 4)))
  (t/is (= 2 (stego/calculate-position-array-size 5)))
  (t/is (= 2 (stego/calculate-position-array-size 8)))
  (t/is (= 3 (stego/calculate-position-array-size 9))))
(t/deftest mark-header-pixels-as-fully-used-test
  (let [positions (byte-array (stego/calculate-position-array-size 100))]
    (stego/mark-header-pixels-as-fully-used! positions)
    (doseq [pixel-index (range stego/header-pixel-count)]
      (t/is (= 3 (#'app.stego/read-position-state positions pixel-index))))))
(t/deftest write-and-read-header-from-image-roundtrip-test
  ;; 56 pixels are enough for 168 header bits
  (let [pixels (int-array 60 0)
        salt (byte-array (map byte (range 16)))
        header (stego/build-header-byte-array 0 true 777 salt)]
    (stego/write-header-into-image! pixels header)
    (let [read-header (stego/read-header-from-image pixels)
          parsed (stego/parse-header-byte-array read-header)]
      (t/is (= 0 (:version-number parsed)))
      (t/is (= true (:use-gzip? parsed)))
      (t/is (= 777 (:encrypted-message-length-in-bytes parsed)))
      (t/is (= (vec salt) (vec (:salt-bytes parsed)))))))
(t/deftest write-and-read-random-payload-roundtrip-test
  (let [pixel-count 500
        pixels (int-array pixel-count 0)
        positions (byte-array (stego/calculate-position-array-size pixel-count))
        password "Password123"
        salt (byte-array (map byte (range 16)))
        {:keys [position-selection-key-bytes]}
        (crypto/derive-key-material-from-password-and-salt password salt)
        payload (.getBytes "Encrypted payload test" StandardCharsets/UTF_8)]
    ;; mark header area as used, exactly like real encryption/decryption
    (stego/mark-header-pixels-as-fully-used! positions)
    ;; write payload
    (stego/write-encrypted-payload-into-random-channels!
     pixels
     positions
     position-selection-key-bytes
     payload)
    ;; create fresh positions array for reading, same initial state
    (let [positions-for-read (byte-array (stego/calculate-position-array-size pixel-count))]
      (stego/mark-header-pixels-as-fully-used! positions-for-read)
      (let [read-payload (stego/read-encrypted-payload-from-random-channels
                          pixels
                          positions-for-read
                          position-selection-key-bytes
                          (alength payload))]
        (t/is (= (vec payload) (vec read-payload)))))))