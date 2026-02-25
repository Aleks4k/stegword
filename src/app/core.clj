(ns app.core
  (:gen-class)
  (:require [seesaw.core :as s]
            [seesaw.font :as font]))
(defn create-ui []
  (let [
        big (font/font :name "Segoe UI" :style :bold :size 24)
        encrypt-btn (doto (s/button :text "Encrypt")
                      (.setFont big))
        decrypt-btn (doto (s/button :text "Decrypt")
                      (.setFont big))
        root (s/vertical-panel
               :border 20
               :items [(s/config! encrypt-btn :preferred-size [320 :by 90])
                       (s/config! decrypt-btn :preferred-size [320 :by 90])])
        frame (s/frame
                :title "Stegword"
                :content root
                :on-close :exit)]
    frame))
(defn start-ui []
  (s/native!)
  (let [frame (create-ui)]
    (s/pack! frame)
    (s/show! frame)))
(defn -main [& _]
  (s/invoke-later
    (start-ui)))