(ns app.validation
  (:import [java.io FileInputStream]))
(defn valid-password? [pw]
  (and (string? pw)
       (>= (count pw) 8)
       (re-find #"[A-Za-z]" pw)
       (re-find #"\d" pw)))
(def png-signature
  [137 80 78 71 13 10 26 10])
(defn png-file?
  [path]
  (try
    (with-open [stream (FileInputStream. path)]
      (let [bytes (byte-array 8)]
        (.read stream bytes)
        (= (map #(bit-and % 0xFF) bytes) ;(bit-and -119 0xFF) converts signed byte to unsigned int.
           png-signature)))
    (catch Exception _
      false)))