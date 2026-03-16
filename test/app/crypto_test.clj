(ns app.crypto-test
  (:require [clojure.test :as t]
            [app.crypto :as crypto])
  (:import (java.nio.charset StandardCharsets)))
(t/deftest gzip-and-gunzip-roundtrip-test
  (let [message "Hello from Stegword! Hello from Stegword! Hello from Stegword!"
        compressed (crypto/gzip-string-to-bytes message)
        decompressed (crypto/gunzip-bytes compressed)
        result (String. decompressed StandardCharsets/UTF_8)]
    (t/is (= message result))))
(t/deftest generate-random-bytes-length-test
  (t/is (= 16 (alength (crypto/generate-random-bytes 16))))
  (t/is (= 32 (alength (crypto/generate-random-bytes 32)))))
(t/deftest derive-key-material-is-deterministic-for-same-password-and-salt
  (let [password "Password123"
        salt (byte-array (map byte (range 16)))
        first-result (crypto/derive-key-material-from-password-and-salt password salt)
        second-result (crypto/derive-key-material-from-password-and-salt password salt)]
    (t/is (= (vec (:encryption-key-bytes first-result))
             (vec (:encryption-key-bytes second-result))))
    (t/is (= (vec (:nonce-bytes first-result))
             (vec (:nonce-bytes second-result))))
    (t/is (= (vec (:position-selection-key-bytes first-result))
             (vec (:position-selection-key-bytes second-result))))))
(t/deftest derive-key-material-changes-when-salt-changes
  (let [password "Password123"
        salt-a (byte-array (map byte (range 16)))
        salt-b (byte-array (map byte (range 1 17)))
        result-a (crypto/derive-key-material-from-password-and-salt password salt-a)
        result-b (crypto/derive-key-material-from-password-and-salt password salt-b)]
    (t/is (not= (vec (:encryption-key-bytes result-a))
                (vec (:encryption-key-bytes result-b))))
    (t/is (not= (vec (:nonce-bytes result-a))
                (vec (:nonce-bytes result-b))))
    (t/is (not= (vec (:position-selection-key-bytes result-a))
                (vec (:position-selection-key-bytes result-b))))))
(t/deftest aes-gcm-encrypt-decrypt-roundtrip-test
  (let [password "Password123"
        salt (byte-array (map byte (range 16)))
        {:keys [encryption-key-bytes nonce-bytes]}
        (crypto/derive-key-material-from-password-and-salt password salt)
        plaintext (.getBytes "Secret message 123" StandardCharsets/UTF_8)
        ciphertext (crypto/aes-gcm-encrypt encryption-key-bytes nonce-bytes plaintext)
        decrypted (crypto/aes-gcm-decrypt encryption-key-bytes nonce-bytes ciphertext)]
    (t/is (= (vec plaintext) (vec decrypted)))))
(t/deftest aes-gcm-decrypt-fails-when-ciphertext-is-modified
  (let [password "Password123"
        salt (byte-array (map byte (range 16)))
        {:keys [encryption-key-bytes nonce-bytes]}
        (crypto/derive-key-material-from-password-and-salt password salt)
        plaintext (.getBytes "Secret message 123" StandardCharsets/UTF_8)
        ciphertext (crypto/aes-gcm-encrypt encryption-key-bytes nonce-bytes plaintext)
        tampered (byte-array ciphertext)]
    (aset-byte tampered 0 (unchecked-byte (bit-xor 1 (bit-and 0xFF (aget tampered 0)))))
    (t/is (thrown? Exception
                   (crypto/aes-gcm-decrypt encryption-key-bytes nonce-bytes tampered)))))