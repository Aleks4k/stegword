(ns app.validation
  (:import [java.io FileInputStream]))
(defn valid-password? [pw]
  (boolean
   (and (string? pw) ;Provera da li je pw string.
        (>= (count pw) 8) ;Provera da li je string duži od 8 karaktera.
        (re-find #"[A-Za-z]" pw) ;Provera da li sadrži bar jedno slovo.
        (re-find #"\d" pw))) ;Provera da li sadrži bar jednu cifru.
  )
(def png-signature
  [137 80 78 71 13 10 26 10])
(defn png-file?
  [path]
  (try
    (with-open [stream (FileInputStream. path)]
      (let [bytes (byte-array 8)]
        (.read stream bytes)
        (= (map #(bit-and % 0xFF) bytes)
           png-signature)))
    (catch Exception _
      false)))