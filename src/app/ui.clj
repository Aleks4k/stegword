(ns app.ui
  (:require [seesaw.core :as s]
            [seesaw.font :as font]
            [seesaw.chooser :as chooser]
            [app.validation :as v]
            [app.actions :as act]))
(defn- show-error! [parent msg]
  (s/alert parent msg :title "Error" :type :error))
(defn- choose-png-file [parent]
  (some-> (chooser/choose-file
           parent
           :type :open
           :multi? false
           :filters [["PNG image" ["png"]]])
          .getAbsolutePath))
(defn- ask-password [parent]
  (let [passwordField  (s/password :columns 20)
        dlg (s/dialog
             :parent parent
             :width 400
             :height 150
             :title "Password"
             :type :question
             :content (s/border-panel
                       :border 10
                       :north "Unesi password:"
                       :center passwordField)
             :option-type :ok-cancel
             :modal? true
             :success-fn (fn [_] (s/with-password* passwordField (fn [characters] (String. characters))))
             :cancel-fn (constantly nil))]
    (s/show! dlg)))
(defn- on-encrypt-click! [frame]
  (if-let [path (choose-png-file frame)]
    (if (v/png-file? path)
      (if-let [pw (ask-password frame)]
        (if (v/valid-password? pw)
          (act/encrypt! path pw)
          (show-error! frame "Password must have at least 8 characters, at least one letter and at least one digit."))
        (show-error! frame "No password entered."))
      (show-error! frame "Not a PNG file."))
    (show-error! frame "No file selected.")))
(defn- create-ui []
  (let [big (font/font :name "Segoe UI" :style :bold :size 24)
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
    (s/listen encrypt-btn :action (fn [_] (on-encrypt-click! frame)))
    frame))
(defn start-ui []
  (s/native!)
  (let [frame (create-ui)]
    (s/pack! frame)
    (s/show! frame)))