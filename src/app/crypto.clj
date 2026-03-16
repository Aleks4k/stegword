(ns app.crypto
  (:import
   (java.io ByteArrayInputStream ByteArrayOutputStream)
   (java.nio.charset StandardCharsets)
   (java.security SecureRandom)
   (java.util Arrays)
   (java.util.zip GZIPInputStream GZIPOutputStream)
   (javax.crypto Cipher Mac SecretKeyFactory)
   (javax.crypto.spec GCMParameterSpec PBEKeySpec SecretKeySpec)))
;; ------------------------------------------------------------
;; Constants
;; ------------------------------------------------------------
(def ^:private secure-random (SecureRandom.))
;; PBKDF2 settings
(def ^:private pbkdf2-iterations 600000)
(def ^:private pbkdf2-output-size-in-bits 256)
;; AES-GCM settings
(def ^:private aes-key-size-in-bytes 32)       ; 32 bytes = 256-bit AES key
(def ^:private aes-gcm-nonce-size-in-bytes 12) ; recommended GCM nonce size
(def ^:private aes-gcm-tag-size-in-bits 128)   ; 128 bits = 16-byte auth tag
;; HKDF "info" labels used to derive different values from the same master key
(def ^:private encryption-key-info-bytes (.getBytes "enc" StandardCharsets/UTF_8))
(def ^:private nonce-info-bytes (.getBytes "nonce" StandardCharsets/UTF_8))
(def ^:private position-selection-key-info-bytes (.getBytes "pos" StandardCharsets/UTF_8))
;; ------------------------------------------------------------
;; Generic helpers
;; ------------------------------------------------------------
(defn generate-random-bytes
  "Generate n cryptographically secure random bytes."
  [n]
  (let [random-byte-array (byte-array n)]
    (.nextBytes secure-random random-byte-array)
    random-byte-array))
(defn gzip-string-to-bytes
  "Convert a string to UTF-8 bytes and then GZIP-compress it."
  [^String input-string]
  (let [input-bytes (.getBytes input-string StandardCharsets/UTF_8)
        output-stream (ByteArrayOutputStream.)]
    (with-open [gzip-output-stream (GZIPOutputStream. output-stream)]
      (.write gzip-output-stream input-bytes 0 (alength input-bytes)))
    (.toByteArray output-stream)))
(defn gunzip-bytes
  "Decompress GZIP-compressed bytes."
  [^bytes compressed-bytes]
  (with-open [byte-input-stream (ByteArrayInputStream. compressed-bytes)
              gzip-input-stream (GZIPInputStream. byte-input-stream)
              output-stream (ByteArrayOutputStream.)]
    (let [read-buffer (byte-array 4096)]
      (loop []
        (let [bytes-read (.read gzip-input-stream read-buffer)]
          (when (pos? bytes-read)
            (.write output-stream read-buffer 0 bytes-read)
            (recur)))))
    (.toByteArray output-stream)))
;; ------------------------------------------------------------
;; PBKDF2 / HMAC / HKDF helpers
;; ------------------------------------------------------------
(defn- derive-master-key-from-password
  "Derive a 256-bit master key from password + salt using PBKDF2-HMAC-SHA256.
   Cleanup:
   - password chars are overwritten with \\u0000
   - PBEKeySpec password is cleared"
  [^String password ^bytes salt-bytes]
  (let [secret-key-factory (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA256")
        password-characters (.toCharArray password)
        password-key-spec (PBEKeySpec.
                           password-characters
                           salt-bytes
                           pbkdf2-iterations
                           pbkdf2-output-size-in-bits)]
    (try
      (.getEncoded (.generateSecret secret-key-factory password-key-spec))
      (finally
        (Arrays/fill password-characters \u0000)
        (.clearPassword password-key-spec)))))
(defn calculate-hmac-sha256
  "Calculate HMAC-SHA256(key, data). Returns 32 bytes."
  [^bytes key-bytes ^bytes data-bytes]
  (let [mac-instance (Mac/getInstance "HmacSHA256")]
    (.init mac-instance (SecretKeySpec. key-bytes "HmacSHA256"))
    (.doFinal mac-instance data-bytes)))
(defn- hkdf-expand
  "HKDF expand phase.
   Parameters:
   - master-key-bytes : base key material
   - info-bytes       : context label, e.g. \"enc\", \"nonce\", \"pos\"
   - output-length    : how many bytes we want
   Repeatedly computes HMAC blocks until enough bytes are produced."
  [^bytes master-key-bytes ^bytes info-bytes output-length]
  (let [result-buffer (ByteArrayOutputStream.)]
    (loop [previous-hmac-block (byte-array 0)
           block-counter 1]
      (if (>= (.size result-buffer) output-length)
        (Arrays/copyOf (.toByteArray result-buffer) output-length)
        (let [hmac-input-buffer (ByteArrayOutputStream.)]
          (.write hmac-input-buffer previous-hmac-block 0 (alength previous-hmac-block))
          (.write hmac-input-buffer info-bytes 0 (alength info-bytes))
          (.write hmac-input-buffer block-counter)
          (let [current-hmac-block (calculate-hmac-sha256 master-key-bytes
                                                          (.toByteArray hmac-input-buffer))]
            (.write result-buffer current-hmac-block 0 (alength current-hmac-block))
            (recur current-hmac-block (inc block-counter))))))))
(defn derive-key-material-from-password-and-salt
  "Given password + salt, derive everything needed by the application:
   - AES encryption key
   - AES-GCM nonce
   - position selection key for steganography"
  [password salt-bytes]
  (let [master-key-bytes (derive-master-key-from-password password salt-bytes)
        encryption-key-bytes (hkdf-expand master-key-bytes
                                          encryption-key-info-bytes
                                          aes-key-size-in-bytes)
        nonce-bytes (hkdf-expand master-key-bytes
                                 nonce-info-bytes
                                 aes-gcm-nonce-size-in-bytes)
        position-selection-key-bytes (hkdf-expand master-key-bytes
                                                  position-selection-key-info-bytes
                                                  aes-key-size-in-bytes)]
    {:encryption-key-bytes encryption-key-bytes
     :nonce-bytes nonce-bytes
     :position-selection-key-bytes position-selection-key-bytes}))
;; ------------------------------------------------------------
;; AES-GCM helpers
;; ------------------------------------------------------------
(defn aes-gcm-encrypt
  "Encrypt plaintext bytes with AES-GCM.
   Returned value is ciphertext + authentication tag."
  [^bytes encryption-key-bytes ^bytes nonce-bytes ^bytes plaintext-bytes]
  (let [cipher-instance (Cipher/getInstance "AES/GCM/NoPadding")
        aes-key-spec (SecretKeySpec. encryption-key-bytes "AES")
        gcm-parameter-spec (GCMParameterSpec. aes-gcm-tag-size-in-bits nonce-bytes)]
    (.init cipher-instance Cipher/ENCRYPT_MODE aes-key-spec gcm-parameter-spec)
    (.doFinal cipher-instance plaintext-bytes)))
(defn aes-gcm-decrypt
  "Decrypt AES-GCM ciphertext bytes.
   If password/nonce/data are wrong or modified, this method throws."
  [^bytes encryption-key-bytes ^bytes nonce-bytes ^bytes ciphertext-bytes]
  (let [cipher-instance (Cipher/getInstance "AES/GCM/NoPadding")
        aes-key-spec (SecretKeySpec. encryption-key-bytes "AES")
        gcm-parameter-spec (GCMParameterSpec. aes-gcm-tag-size-in-bits nonce-bytes)]
    (.init cipher-instance Cipher/DECRYPT_MODE aes-key-spec gcm-parameter-spec)
    (.doFinal cipher-instance ciphertext-bytes)))