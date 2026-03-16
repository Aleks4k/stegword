(ns app.actions
  (:require [app.crypto :as crypto]
            [app.image :as image]
            [app.stego :as stego])
  (:import
   (java.nio.charset StandardCharsets)
   (javax.swing JOptionPane)))
;; ------------------------------------------------------------
;; Constants
;; ------------------------------------------------------------
;; Max value that can fit into an unsigned 32-bit integer
(def ^:private max-unsigned-32-bit-value 4294967295)
;; ------------------------------------------------------------
;; UI helper used by actions
;; ------------------------------------------------------------
(defn- user-confirmed-encryption?
  "Show a confirmation dialog telling the user how much of the image capacity will be used."
  [used-capacity-percentage used-bits available-bits]
  (= JOptionPane/YES_OPTION
     (JOptionPane/showConfirmDialog
      nil
      (format
       "This action will use %.2f%% of RGB data.%n%nUsed bits: %d%nCapacity bits: %d%n%nContinue?"
       used-capacity-percentage
       used-bits
       available-bits)
      "Confirm encryption"
      JOptionPane/YES_NO_OPTION
      JOptionPane/QUESTION_MESSAGE)))
;; ------------------------------------------------------------
;; Public API
;; ------------------------------------------------------------
(defn encrypt!
  "Encrypt a message and hide it inside a PNG image.
   Flow:
   1. Convert message to bytes
   2. GZIP it only if that makes it smaller
   3. Generate salt
   4. Derive keys and nonce from password + salt
   5. Encrypt payload with AES-GCM
   6. Build header
   7. Write header linearly and encrypted payload pseudo-randomly
   8. Save new image"
  [path password message]
  (try
    (let [original-message-bytes (.getBytes message StandardCharsets/UTF_8)
          gzipped-message-bytes (crypto/gzip-string-to-bytes message)
          ;; Use GZIP only if it actually makes the message smaller.
          use-gzip? (< (alength gzipped-message-bytes) (alength original-message-bytes))
          plaintext-payload-bytes (if use-gzip?
                                    gzipped-message-bytes
                                    original-message-bytes)
          salt-bytes (crypto/generate-random-bytes 16)
          {:keys [encryption-key-bytes nonce-bytes position-selection-key-bytes]}
          (crypto/derive-key-material-from-password-and-salt password salt-bytes)
          encrypted-message-bytes (crypto/aes-gcm-encrypt encryption-key-bytes
                                                          nonce-bytes
                                                          plaintext-payload-bytes)
          encrypted-message-length-in-bytes (alength encrypted-message-bytes)
          required-image-capacity-in-bits (+ stego/header-size-in-bits
                                             (* 8 encrypted-message-length-in-bytes))
          available-image-capacity-in-bits (image/calculate-image-capacity-in-bits path)
          used-capacity-percentage (* 100.0
                                      (/ required-image-capacity-in-bits
                                         (double available-image-capacity-in-bits)))]
      (cond
        (> encrypted-message-length-in-bytes max-unsigned-32-bit-value)
        {:success false
         :message "Encrypted message length does not fit into 4-byte unsigned header."}
        (> required-image-capacity-in-bits available-image-capacity-in-bits)
        {:success false
         :message (format
                   "Encrypted message does not fit into image. Needed: %d bits, capacity: %d bits."
                   required-image-capacity-in-bits
                   available-image-capacity-in-bits)}
        (not (user-confirmed-encryption?
              used-capacity-percentage
              required-image-capacity-in-bits
              available-image-capacity-in-bits))
        {:success false
         :message "Encryption cancelled by user."}
        :else
        (let [image-object (image/load-image-as-rgb path)
              image-width (.getWidth image-object)
              image-height (.getHeight image-object)
              image-pixels (image/get-image-pixels image-object)
              ;; positions array tracks how many channels were already used for each pixel
              pixel-position-state-array (byte-array
                                          (stego/calculate-position-array-size (* image-width image-height)))
              header-byte-array (stego/build-header-byte-array stego/current-header-version
                                                               use-gzip?
                                                               encrypted-message-length-in-bytes
                                                               salt-bytes)
              output-image-path (image/build-encrypted-output-path path)]
          ;; Header is always stored at the beginning of the image
          (stego/mark-header-pixels-as-fully-used! pixel-position-state-array)
          (stego/write-header-into-image! image-pixels header-byte-array)
          ;; Encrypted payload goes to pseudo-random positions
          (stego/write-encrypted-payload-into-random-channels!
           image-pixels
           pixel-position-state-array
           position-selection-key-bytes
           encrypted-message-bytes)
          ;; Save resulting image
          (image/save-png! image-object output-image-path)
          {:success true
           :message (format
                     "Image encrypted successfully. Saved to: %s | Gzip used: %s | Message length: %d bytes | RGB usage: %.2f%%"
                     output-image-path
                     use-gzip?
                     encrypted-message-length-in-bytes
                     used-capacity-percentage)})))
    (catch Exception exception
      {:success false
       :message (or (.getMessage exception)
                    "Unknown error occurred during encryption.")})))
(defn decrypt!
  "Read hidden encrypted data from a PNG image and decrypt it.
   Flow:
   1. Read header from the first 168 bits
   2. Parse version, gzip flag, encrypted length and salt
   3. Re-derive keys from password + salt
   4. Rebuild encrypted payload from pseudo-random positions
   5. AES-GCM decrypt
   6. Gunzip if header says gzip was used
   7. Return recovered plaintext message"
  [path password]
  (try
    (let [image-object (image/load-image-as-rgb path)
          image-width (.getWidth image-object)
          image-height (.getHeight image-object)
          image-pixels (image/get-image-pixels image-object)
          header-byte-array (stego/read-header-from-image image-pixels)
          {:keys [version-number
                  use-gzip?
                  encrypted-message-length-in-bytes
                  salt-bytes]}
          (stego/parse-header-byte-array header-byte-array)]
      (cond
        (not= version-number stego/current-header-version)
        {:success false
         :message (str "Unsupported header version: " version-number)}
        (> encrypted-message-length-in-bytes Integer/MAX_VALUE)
        {:success false
         :message "Encrypted message length is too large for this runtime."}
        (> (+ stego/header-size-in-bits (* 8 encrypted-message-length-in-bytes))
           (* image-width image-height 3))
        {:success false
         :message "Encrypted message does not fit image capacity. File may be corrupted."}
        :else
        (let [{:keys [encryption-key-bytes nonce-bytes position-selection-key-bytes]}
              (crypto/derive-key-material-from-password-and-salt password salt-bytes)
              pixel-position-state-array (byte-array
                                          (stego/calculate-position-array-size (* image-width image-height)))
              _ (stego/mark-header-pixels-as-fully-used! pixel-position-state-array)
              encrypted-message-byte-array (stego/read-encrypted-payload-from-random-channels
                                            image-pixels
                                            pixel-position-state-array
                                            position-selection-key-bytes
                                            encrypted-message-length-in-bytes)
              decrypted-plaintext-bytes (crypto/aes-gcm-decrypt encryption-key-bytes
                                                                nonce-bytes
                                                                encrypted-message-byte-array)
              final-message-bytes (if use-gzip?
                                    (crypto/gunzip-bytes decrypted-plaintext-bytes)
                                    decrypted-plaintext-bytes)
              final-message-string (String. final-message-bytes StandardCharsets/UTF_8)]
          {:success true
           :message final-message-string})))
    (catch Exception exception
      {:success false
       :message (or (.getMessage exception)
                    "Unknown error occurred during decryption.")})))