(ns app.actions-test
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [app.actions :as actions]
            [app.image :as image])
  (:import (java.nio.file Files)))
(def test-image-path "test/images/image.png")
(defn- create-temp-png-copy!
  []
  (let [temp-dir (.toFile (Files/createTempDirectory "stegword-test"
                                                     (make-array java.nio.file.attribute.FileAttribute 0)))
        temp-image-file (io/file temp-dir "image.png")]
    (io/copy (io/file test-image-path) temp-image-file)
    (.getPath temp-image-file)))
(t/deftest encrypt-success-test
  (let [temp-image-path (create-temp-png-copy!)
        output-path (image/build-encrypted-output-path temp-image-path)]
    (with-redefs [app.actions/user-confirmed-encryption? (fn [& _] true)]
      (let [result (actions/encrypt! temp-image-path "Password123" "Hello world")]
        (t/is (= true (:success result)))
        (t/is (.exists (io/file output-path)))))))
(t/deftest encrypt-cancel-test
  (let [temp-image-path (create-temp-png-copy!)]
    (with-redefs [app.actions/user-confirmed-encryption? (fn [& _] false)]
      (let [result (actions/encrypt! temp-image-path "Password123" "Hello world")]
        (t/is (= false (:success result)))
        (t/is (= "Encryption cancelled by user." (:message result)))))))
(t/deftest encrypt-and-decrypt-roundtrip-test
  (let [temp-image-path (create-temp-png-copy!)
        output-path (image/build-encrypted-output-path temp-image-path)
        original-message "Hello from roundtrip test!"]
    (with-redefs [app.actions/user-confirmed-encryption? (fn [& _] true)]
      (let [encrypt-result (actions/encrypt! temp-image-path "Password123" original-message)]
        (t/is (= true (:success encrypt-result)))
        (let [decrypt-result (actions/decrypt! output-path "Password123")]
          (t/is (= true (:success decrypt-result)))
          (t/is (= original-message (:message decrypt-result))))))))
(t/deftest decrypt-fails-with-wrong-password-test
  (let [temp-image-path (create-temp-png-copy!)
        output-path (image/build-encrypted-output-path temp-image-path)]
    (with-redefs [app.actions/user-confirmed-encryption? (fn [& _] true)]
      (let [encrypt-result (actions/encrypt! temp-image-path "Password123" "Very secret message")]
        (t/is (= true (:success encrypt-result)))
        (let [decrypt-result (actions/decrypt! output-path "WrongPassword123")]
          (t/is (= false (:success decrypt-result))))))))
(t/deftest encrypt-fails-when-message-does-not-fit-into-image-test
  (let [temp-image-path (create-temp-png-copy!)
        huge-message (apply str (repeatedly 2000000 #(char (+ (rand-int 26) (int \a)))))] ;;Very large message that won't fit into the image.
    (with-redefs [app.actions/user-confirmed-encryption? (fn [& _] true)]
      (let [result (actions/encrypt! temp-image-path "Password123" huge-message)]
        (t/is (= false (:success result)))))))