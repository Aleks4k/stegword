(ns app.ui
  (:require [seesaw.core :as s]
            [seesaw.font :as font]
            [seesaw.border :as border]
            [seesaw.chooser :as chooser]
            [app.validation :as v]
            [app.actions :as act]))
;; ------------------------------------------------------------
;; Generic dialogs
;; ------------------------------------------------------------
(defn- show-error!
  "Show error dialog."
  [parent message]
  (s/alert parent message :title "Error" :type :error))
(defn- show-info!
  "Show simple information dialog."
  [parent message]
  (s/alert parent message :title "Info" :type :info))
(defn- show-message-dialog!
  "Show a larger scrollable dialog for long text.
   This is useful for decrypted messages because alert dialogs are too small."
  [parent title message]
  (let [message-area (doto
                      (s/text :multi-line? true
                              :wrap-lines? true
                              :editable? false
                              :rows 12
                              :columns 40)
                       (s/text! message))
        dialog (s/dialog
                :parent parent
                :title title
                :width 520
                :height 340
                :modal? true
                :content (s/border-panel
                          :border 12
                          :center (s/scrollable message-area)
                          :south (s/horizontal-panel
                                  :border 10
                                  :items [(s/button :text "Close"
                                                    :listen [:action (fn [event]
                                                                       (-> event .getSource s/to-root .dispose))])])))]
    (s/show! dialog)))
(defn- handle-action-result!
  "Centralized result handling for encrypt/decrypt actions.

   If action was successful:
   - show normal info dialog
   - or show large text dialog when `use-message-dialog?` is true

   If action failed:
   - show error dialog"
  [frame result success-title use-message-dialog?]
  (if (:success result)
    (if use-message-dialog?
      (show-message-dialog! frame success-title (:message result))
      (show-info! frame (:message result)))
    (show-error! frame (:message result))))
;; ------------------------------------------------------------
;; File chooser
;; ------------------------------------------------------------
(defn- choose-png-file
  "Open file chooser and return selected PNG file path or nil."
  [parent]
  (some-> (chooser/choose-file
           parent
           :type :open
           :multi? false
           :filters [["PNG image" ["png"]]])
          .getAbsolutePath))
(defn- choose-valid-png-file
  "Choose PNG file and validate it.
   Returns valid path or nil.
   Shows error dialog on invalid selection."
  [frame]
  (if-let [path (choose-png-file frame)]
    (if (v/png-file? path)
      path
      (do
        (show-error! frame "Not a PNG file.")
        nil))
    (do
      (show-error! frame "No file selected.")
      nil)))
;; ------------------------------------------------------------
;; Input dialogs
;; ------------------------------------------------------------
(defn- ask-password
  "Ask user for password. Returns password string or nil."
  [parent]
  (let [password-field (s/password :columns 20)
        dialog (s/dialog
                :parent parent
                :width 400
                :height 150
                :title "Password"
                :type :question
                :content (s/border-panel
                          :border 10
                          :north "Enter password:"
                          :center password-field)
                :option-type :ok-cancel
                :modal? true
                :success-fn (fn [_]
                              (s/with-password* password-field
                                (fn [characters]
                                  (String. characters))))
                :cancel-fn (constantly nil))]
    (s/show! dialog)))
(defn- prompt-valid-password
  "Ask user for password and validate it.
   Returns valid password or nil.
   Shows error dialog on invalid input."
  [frame]
  (if-let [password (ask-password frame)]
    (if (v/valid-password? password)
      password
      (do
        (show-error! frame "Password must have at least 8 characters, at least one letter and at least one digit.")
        nil))
    (do
      (show-error! frame "No password entered.")
      nil)))
(defn- ask-message
  "Ask user for message to encrypt. Returns message string or nil."
  [parent]
  (let [message-field (s/text :multi-line? true
                              :wrap-lines? true
                              :rows 8
                              :columns 30)
        dialog (s/dialog
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
    (s/show! dialog)))
(defn- prompt-valid-message
  "Ask user for encryption message and validate it.
   Returns valid message or nil.
   Shows error dialog on invalid input."
  [frame]
  (if-let [message (ask-message frame)]
    (if (v/valid-message? message)
      message
      (do
        (show-error! frame "Message cannot be empty.")
        nil))
    (do
      (show-error! frame "No message entered.")
      nil)))
;; ------------------------------------------------------------
;; Action handlers
;; ------------------------------------------------------------
(defn- on-encrypt-click!
  "Encrypt flow:
   1. choose PNG
   2. ask password
   3. ask message
   4. call encrypt!
   5. show result"
  [frame]
  (when-let [path (choose-valid-png-file frame)]
    (when-let [password (prompt-valid-password frame)]
      (when-let [message (prompt-valid-message frame)]
        (let [result (act/encrypt! path password message)]
          (handle-action-result! frame result "Encryption Result" false))))))
(defn- on-decrypt-click!
  "Decrypt flow:
   1. choose PNG
   2. ask password
   3. call decrypt!
   4. show decrypted message in large scrollable dialog"
  [frame]
  (when-let [path (choose-valid-png-file frame)]
    (when-let [password (prompt-valid-password frame)]
      (let [result (act/decrypt! path password)]
        (handle-action-result! frame result "Decrypted Message" true)))))
;; ------------------------------------------------------------
;; UI layout helpers
;; ------------------------------------------------------------
(defn- v-gap
  "Vertical empty space."
  [size]
  (javax.swing.Box/createVerticalStrut size))
;; ------------------------------------------------------------
;; Main UI
;; ------------------------------------------------------------
(defn- create-ui []
  (let [title-font (font/font :name "Segoe UI" :style :bold :size 28)
        subtitle-font (font/font :name "Segoe UI" :style :plain :size 13)
        button-font (font/font :name "Segoe UI" :style :bold :size 18)
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
        encrypt-button (doto
                        (s/button :text "Encrypt"
                                  :focusable? false
                                  :background "#2563EB"
                                  :foreground "#FFFFFF")
                         (.setFont button-font))
        decrypt-button (doto
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
                               (s/config! encrypt-button :preferred-size [340 :by 65])
                               (v-gap 15)
                               (s/config! decrypt-button :preferred-size [340 :by 65])])
        wrapper-panel (s/border-panel
                       :background "#F3F4F6"
                       :border (border/empty-border)
                       :center content-panel)
        frame (s/frame
               :title "Stegword"
               :content wrapper-panel
               :on-close :exit
               :resizable? false)]
    (s/listen encrypt-button :action (fn [_] (on-encrypt-click! frame)))
    (s/listen decrypt-button :action (fn [_] (on-decrypt-click! frame)))
    frame))
(defn start-ui []
  (let [frame (create-ui)]
    (s/pack! frame)
    (s/show! frame)))