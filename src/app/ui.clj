(ns app.ui
  (:require [seesaw.core :as s]
            [seesaw.font :as font]
            [seesaw.border :as border]
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
                       :north "Enter password:"
                       :center passwordField)
             :option-type :ok-cancel
             :modal? true
             :success-fn (fn [_] (s/with-password* passwordField (fn [characters] (String. characters))))
             :cancel-fn (constantly nil))]
    (s/show! dlg)))
(defn- ask-message [parent]
  (let [message-field (s/text :multi-line? true
                              :wrap-lines? true
                              :rows 8
                              :columns 30)
        dlg (s/dialog
             :parent parent
             :width 450
             :height 250
             :title "Message"
             :type :question
             :content (s/border-panel
                       :border 10
                       :north "Enter message to encrypt:"
                       :center (s/scrollable message-field))
             :option-type :ok-cancel
             :modal? true
             :success-fn (fn [_] (s/text message-field))
             :cancel-fn (constantly nil))]
    (s/show! dlg)))
(defn- on-encrypt-click! [frame]
  (if-let [path (choose-png-file frame)]
    (if (v/png-file? path)
      (if-let [pw (ask-password frame)]
        (if (v/valid-password? pw)
          (if-let [message (ask-message frame)]
             (if (v/valid-message? message)
               (act/encrypt! path pw message)
               (show-error! frame "Message cannot be empty."))
             (show-error! frame "No message entered."))
           (show-error! frame "Password must have at least 8 characters, at least one letter and at least one digit."))
        (show-error! frame "No password entered."))
      (show-error! frame "Not a PNG file."))
    (show-error! frame "No file selected.")))
(defn- v-gap [size]
  (javax.swing.Box/createVerticalStrut size))
(defn- create-ui []
  (let [title-font   (font/font :name "Segoe UI" :style :bold :size 28)
        subtitle-font (font/font :name "Segoe UI" :style :plain :size 13)
        button-font  (font/font :name "Segoe UI" :style :bold :size 18)
        title-label (s/label
                     :text "Stegword"
                     :font title-font
                     :foreground "#1F2937"
                     :halign :center)
        subtitle-label (s/label
                        :text "Encrypt and decrypt hidden text inside PNG images"
                        :font subtitle-font
                        :foreground "#6B7280"
                        :halign :center)
        encrypt-btn (doto
                     (s/button :text "Encrypt"
                               :focusable? false
                               :background "#2563EB"
                               :foreground "#FFFFFF")
                      (.setFont button-font))
        decrypt-btn (doto
                     (s/button :text "Decrypt"
                               :focusable? false
                               :background "#10B981"
                               :foreground "#FFFFFF")
                      (.setFont button-font))
        content-panel (s/vertical-panel
                       :background "#FFFFFF"
                       :border (border/empty-border :top 25 :left 25 :bottom 25 :right 25)
                       :items [title-label
                               (v-gap 8)
                               subtitle-label
                               (v-gap 25)
                               (s/config! encrypt-btn :preferred-size [340 :by 65])
                               (v-gap 15)
                               (s/config! decrypt-btn :preferred-size [340 :by 65])])
        wrapper (s/border-panel
                 :background "#F3F4F6"
                 :border (border/empty-border)
                 :center content-panel)
        frame (s/frame
               :title "Stegword"
               :content wrapper
               :on-close :exit
               :resizable? false)]
    (s/listen encrypt-btn :action (fn [_] (on-encrypt-click! frame)))
    frame))
(defn start-ui []
  (let [frame (create-ui)]
    (s/pack! frame)
    (s/show! frame)))