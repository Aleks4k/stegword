(ns app.stego
  (:require [app.crypto :as crypto])
  (:import
   (java.nio ByteBuffer)))

;; ------------------------------------------------------------
;; Header constants
;; ------------------------------------------------------------
(def ^:private unsigned-32-bit-space-size 4294967296)
;; Header layout:
;; 1 byte  = version (7 bits) + gzip flag (1 bit)
;; 4 bytes = encrypted message length
;; 16 bytes = salt
(def header-size-in-bytes 21)
(def header-size-in-bits (* header-size-in-bytes 8))
(def header-pixel-count (quot header-size-in-bits 3))
(def current-header-version 0)

;; ------------------------------------------------------------
;; Position state constants
;; ------------------------------------------------------------

;; Position state encoding:
;; 0 = no channel used yet
;; 1 = R channel already used
;; 2 = R and G channels already used
;; 3 = R, G and B channels already used
(def ^:private position-state-rgb-used 3)

;; ------------------------------------------------------------
;; Header helpers
;; ------------------------------------------------------------

(defn build-header-byte-array
  "Create the 21-byte header.
   Layout:
   byte 0      = version (top 7 bits) + gzip flag (last bit)
   bytes 1..4  = encrypted message length
   bytes 5..20 = salt"
  [version-number use-gzip? encrypted-message-length-in-bytes ^bytes salt-bytes]
  (let [header-byte-array (byte-array header-size-in-bytes)
        first-header-byte (bit-or (bit-shift-left version-number 1)
                                  (if use-gzip? 1 0))
        encrypted-message-length-bytes (.array
                                        (doto (ByteBuffer/allocate 4)
                                          (.putInt (int encrypted-message-length-in-bytes))))]
    (aset-byte header-byte-array 0 (unchecked-byte first-header-byte))
    (System/arraycopy encrypted-message-length-bytes 0 header-byte-array 1 4)
    (System/arraycopy salt-bytes 0 header-byte-array 5 16)
    header-byte-array))

(defn- read-unsigned-32-bit-at-offset
  "Read 4 bytes from a byte array as one unsigned 32-bit value."
  [^bytes byte-array offset]
  (let [byte-0 (long (bit-and 0xFF (aget byte-array offset)))
        byte-1 (long (bit-and 0xFF (aget byte-array (+ offset 1))))
        byte-2 (long (bit-and 0xFF (aget byte-array (+ offset 2))))
        byte-3 (long (bit-and 0xFF (aget byte-array (+ offset 3))))]
    (+ (bit-shift-left byte-0 24)
       (bit-shift-left byte-1 16)
       (bit-shift-left byte-2 8)
       byte-3)))

(defn parse-header-byte-array
  "Read version, gzip flag, encrypted message length and salt from the header."
  [^bytes header-byte-array]
  (let [first-header-byte (bit-and 0xFF (aget header-byte-array 0))
        version-number (bit-shift-right first-header-byte 1)
        use-gzip? (= 1 (bit-and first-header-byte 0x01))
        encrypted-message-length-in-bytes (read-unsigned-32-bit-at-offset header-byte-array 1)
        salt-bytes (byte-array 16)]
    (System/arraycopy header-byte-array 5 salt-bytes 0 16)
    {:version-number version-number
     :use-gzip? use-gzip?
     :encrypted-message-length-in-bytes encrypted-message-length-in-bytes
     :salt-bytes salt-bytes}))

;; ------------------------------------------------------------
;; Bit helpers
;; ------------------------------------------------------------

(defn- read-bit-from-byte-array
  "Read one bit from a byte array.
   Bit order inside each byte is:
   bit 0 = most significant bit
   bit 7 = least significant bit"
  [^bytes byte-array bit-index]
  (let [byte-index (quot bit-index 8)
        bit-index-inside-byte (mod bit-index 8)
        current-byte-value (bit-and 0xFF (aget byte-array byte-index))]
    (bit-and 1 (bit-shift-right current-byte-value (- 7 bit-index-inside-byte)))))

(defn- write-bit-to-byte-array!
  "Write a single bit into a byte array."
  [^bytes byte-array bit-index bit-value]
  (let [byte-index (quot bit-index 8)
        bit-shift (- 7 (mod bit-index 8))
        current-byte-value (bit-and 0xFF (aget byte-array byte-index))
        byte-value-with-cleared-target-bit (bit-and current-byte-value
                                                    (bit-not (bit-shift-left 1 bit-shift)))
        updated-byte-value (if (zero? bit-value)
                             byte-value-with-cleared-target-bit
                             (bit-or byte-value-with-cleared-target-bit
                                     (bit-shift-left 1 bit-shift)))]
    (aset-byte byte-array byte-index (unchecked-byte updated-byte-value))))

(defn- long-counter-to-byte-array
  "Convert a counter number to 8 bytes.
   This counter is used as input to HMAC when generating pseudo-random positions."
  [counter-value]
  (.array
   (doto (ByteBuffer/allocate 8)
     (.putLong (long counter-value)))))

;; ------------------------------------------------------------
;; Pixel channel helpers
;; ------------------------------------------------------------

(defn- set-channel-least-significant-bit
  "Set the LSB of one channel inside one RGB pixel integer.
   channel-index:
   0 = R
   1 = G
   2 = B"
  [pixel-value channel-index bit-value]
  (let [bit-shift (case channel-index
                    0 16 ; least-significant bit of Red channel
                    1 8  ; least-significant bit of Green channel
                    2 0) ; least-significant bit of Blue channel
        target-bit-mask (bit-shift-left 1 bit-shift)] ;We will use this mask to set target bit to 0 or 1. The rest of the bits in the pixel value will be left unchanged.
    (int
     (if (zero? bit-value)
       (bit-and pixel-value (bit-not target-bit-mask))
       (bit-or pixel-value target-bit-mask)))))

(defn- read-channel-least-significant-bit
  "Read the LSB of one channel from one RGB pixel integer.

   channel-index:
   0 = R
   1 = G
   2 = B"
  [pixel-value channel-index]
  (case channel-index
    0 (bit-and 1 (bit-shift-right pixel-value 16))
    1 (bit-and 1 (bit-shift-right pixel-value 8))
    2 (bit-and 1 pixel-value)))

;; ------------------------------------------------------------
;; Positions array helpers
;; ------------------------------------------------------------

(defn calculate-position-array-size
  "Each byte stores 4 pixels, because each pixel needs only 2 bits of state.
   We round up so that the last partial group still has room."
  [total-pixel-count]
  (quot (+ total-pixel-count 3) 4))

(defn- calculate-position-array-index
  "Which byte inside the positions array stores state for this pixel?"
  [pixel-index]
  (quot pixel-index 4))

(defn- calculate-position-bit-shift
  "Inside one positions byte:
   pixel 0 uses bits 7..6
   pixel 1 uses bits 5..4
   pixel 2 uses bits 3..2
   pixel 3 uses bits 1..0"
  [pixel-index]
  (- 6 (* 2 (mod pixel-index 4))))

(defn- read-position-state
  "Read 2-bit state for one pixel from the positions array."
  [^bytes pixel-position-state-array pixel-index]
  (let [positions-byte-index (calculate-position-array-index pixel-index)
        bit-shift (calculate-position-bit-shift pixel-index)
        positions-byte-value (bit-and 0xFF (aget pixel-position-state-array positions-byte-index))]
    (bit-and 0x03 (bit-shift-right positions-byte-value bit-shift))))

(defn- write-position-state!
  "Write 2-bit state for one pixel into the positions array."
  [^bytes pixel-position-state-array pixel-index new-state]
  (let [positions-byte-index (calculate-position-array-index pixel-index)
        bit-shift (calculate-position-bit-shift pixel-index)
        current-byte-value (bit-and 0xFF (aget pixel-position-state-array positions-byte-index))
        byte-value-with-cleared-state-bits (bit-and current-byte-value
                                                    (bit-not (bit-shift-left 0x03 bit-shift)))
        updated-byte-value (bit-or byte-value-with-cleared-state-bits
                                   (bit-shift-left (bit-and new-state 0x03) bit-shift))]
    (aset-byte pixel-position-state-array positions-byte-index (unchecked-byte updated-byte-value))))

(defn mark-header-pixels-as-fully-used!
  "Header uses the first 168 bits linearly.
   168 bits / 3 channels = 56 fully used pixels.
   Mark those pixels as RGB fully used so random payload writing will skip them."
  [^bytes pixel-position-state-array]
  (dotimes [pixel-index header-pixel-count]
    (write-position-state! pixel-position-state-array pixel-index position-state-rgb-used)))

;; ------------------------------------------------------------
;; Header image helpers
;; ------------------------------------------------------------

(defn write-header-into-image!
  "Write the 168 header bits linearly into the first channel slots of the image.
   Order:
   bit 0 -> pixel 0, R channel
   bit 1 -> pixel 0, G channel
   bit 2 -> pixel 0, B channel
   bit 3 -> pixel 1, R channel
   ..."
  [^ints image-pixels ^bytes header-byte-array]
  (dotimes [header-bit-index header-size-in-bits]
    (let [pixel-index (quot header-bit-index 3)
          channel-index (mod header-bit-index 3)
          bit-value (read-bit-from-byte-array header-byte-array header-bit-index)
          current-pixel-value (aget image-pixels pixel-index)]
      (aset-int image-pixels
                pixel-index
                (set-channel-least-significant-bit current-pixel-value
                                                   channel-index
                                                   bit-value)))))

(defn read-header-from-image
  "Read the first 168 bits linearly from image pixels and reconstruct the header byte array."
  [^ints image-pixels]
  (let [header-byte-array (byte-array header-size-in-bytes)]
    (dotimes [header-bit-index header-size-in-bits]
      (let [pixel-index (quot header-bit-index 3)
            channel-index (mod header-bit-index 3)
            current-pixel-value (aget image-pixels pixel-index)
            bit-value (read-channel-least-significant-bit current-pixel-value channel-index)]
        (write-bit-to-byte-array! header-byte-array header-bit-index bit-value)))
    header-byte-array))

;; ------------------------------------------------------------
;; Random payload image helpers
;; ------------------------------------------------------------

(defn- calculate-unbiased-selection-limit
  "Returns the largest value < 2^32 that is evenly divisible by total-pixel-count.
   Random 32-bit values >= this limit are rejected to avoid modulo bias."
  [total-pixel-count]
  (* (quot unsigned-32-bit-space-size total-pixel-count)
     total-pixel-count))
(defn- try-convert-random-u32-to-pixel-index
  "Convert a random unsigned 32-bit value to a pixel index without modulo bias.
   Returns nil if the value falls into the rejected tail range."
  [random-u32 total-pixel-count unbiased-selection-limit]
  (when (< random-u32 unbiased-selection-limit)
    (int (mod random-u32 total-pixel-count))))
(defn- next-random-pixel-candidate
  "Reads the next 4-byte random value from the current HMAC block.
   Generates a new HMAC block when needed.
   Returns a map with:
   - :candidate-pixel-index (or nil if rejected by rejection sampling)
   - :current-random-choice-block
   - :next-random-block-counter
   - :next-byte-offset"
  [^bytes position-selection-key-bytes
   total-pixel-count
   unbiased-selection-limit
   random-block-counter
   ^bytes random-choice-block
   byte-offset-inside-random-block]
  (let [[current-random-choice-block next-random-block-counter next-byte-offset-inside-random-block]
        (if (> byte-offset-inside-random-block 28)
          [(crypto/calculate-hmac-sha256 position-selection-key-bytes
                                         (long-counter-to-byte-array random-block-counter))
           (inc random-block-counter)
           0]
          [random-choice-block
           random-block-counter
           byte-offset-inside-random-block])
        random-u32 (read-unsigned-32-bit-at-offset
                    current-random-choice-block
                    next-byte-offset-inside-random-block)
        candidate-pixel-index (try-convert-random-u32-to-pixel-index
                               random-u32
                               total-pixel-count
                               unbiased-selection-limit)
        next-byte-offset (+ next-byte-offset-inside-random-block 4)]
    {:candidate-pixel-index candidate-pixel-index
     :current-random-choice-block current-random-choice-block
     :next-random-block-counter next-random-block-counter
     :next-byte-offset next-byte-offset}))
(defn write-encrypted-payload-into-random-channels!
  "Write encrypted payload bits into pseudo-random pixel/channel locations.
   Rules:
   - header pixels are skipped
   - if a pixel already used all RGB channels, it is skipped
   - pixel selection is derived from HMAC(position-selection-key, counter)
   - every 4 bytes from HMAC output are used as one candidate pixel index
   - rejection sampling is used to avoid modulo bias"
  [^ints image-pixels ^bytes pixel-position-state-array ^bytes position-selection-key-bytes ^bytes encrypted-message-bytes]
  (let [total-pixel-count (alength image-pixels)
        encrypted-message-bit-count (* 8 (alength encrypted-message-bytes))
        unbiased-selection-limit (calculate-unbiased-selection-limit total-pixel-count)]
    (loop [encrypted-message-bit-index 0
           random-block-counter 0
           random-choice-block (byte-array 0)
           byte-offset-inside-random-block 32] ; force generation of the first HMAC block
      (if (= encrypted-message-bit-index encrypted-message-bit-count)
        nil
        (let [{:keys [candidate-pixel-index
                      current-random-choice-block
                      next-random-block-counter
                      next-byte-offset]}
              (next-random-pixel-candidate
               position-selection-key-bytes
               total-pixel-count
               unbiased-selection-limit
               random-block-counter
               random-choice-block
               byte-offset-inside-random-block)]
          (if (or (nil? candidate-pixel-index)
                  (< candidate-pixel-index header-pixel-count)
                  (= position-state-rgb-used
                     (read-position-state pixel-position-state-array candidate-pixel-index)))
            (recur encrypted-message-bit-index
                   next-random-block-counter
                   current-random-choice-block
                   next-byte-offset)
            (let [next-free-channel-index (read-position-state pixel-position-state-array candidate-pixel-index)
                  bit-value-to-write (read-bit-from-byte-array encrypted-message-bytes
                                                               encrypted-message-bit-index)
                  current-pixel-value (aget image-pixels candidate-pixel-index)]
              (aset-int image-pixels
                        candidate-pixel-index
                        (set-channel-least-significant-bit current-pixel-value
                                                           next-free-channel-index
                                                           bit-value-to-write))
              (write-position-state! pixel-position-state-array
                                     candidate-pixel-index
                                     (inc next-free-channel-index))
              (recur (inc encrypted-message-bit-index)
                     next-random-block-counter
                     current-random-choice-block
                     next-byte-offset))))))))
(defn read-encrypted-payload-from-random-channels
  "Read encrypted payload back using the same pseudo-random channel selection logic
   that was used during encryption.
   Rejection sampling is used to avoid modulo bias."
  [^ints image-pixels ^bytes pixel-position-state-array ^bytes position-selection-key-bytes encrypted-message-length-in-bytes]
  (let [encrypted-message-byte-array (byte-array (int encrypted-message-length-in-bytes))
        encrypted-message-bit-count (* 8 encrypted-message-length-in-bytes)
        total-pixel-count (alength image-pixels)
        unbiased-selection-limit (calculate-unbiased-selection-limit total-pixel-count)]
    (loop [encrypted-message-bit-index 0
           random-block-counter 0
           random-choice-block (byte-array 0)
           byte-offset-inside-random-block 32]
      (if (= encrypted-message-bit-index encrypted-message-bit-count)
        encrypted-message-byte-array
        (let [{:keys [candidate-pixel-index
                      current-random-choice-block
                      next-random-block-counter
                      next-byte-offset]}
              (next-random-pixel-candidate
               position-selection-key-bytes
               total-pixel-count
               unbiased-selection-limit
               random-block-counter
               random-choice-block
               byte-offset-inside-random-block)]
          (if (or (nil? candidate-pixel-index)
                  (< candidate-pixel-index header-pixel-count)
                  (= position-state-rgb-used
                     (read-position-state pixel-position-state-array candidate-pixel-index)))
            (recur encrypted-message-bit-index
                   next-random-block-counter
                   current-random-choice-block
                   next-byte-offset)
            (let [next-free-channel-index (read-position-state pixel-position-state-array candidate-pixel-index)
                  current-pixel-value (aget image-pixels candidate-pixel-index)
                  bit-value-read-from-image (read-channel-least-significant-bit current-pixel-value
                                                                                next-free-channel-index)]
              (write-bit-to-byte-array! encrypted-message-byte-array
                                        encrypted-message-bit-index
                                        bit-value-read-from-image)
              (write-position-state! pixel-position-state-array
                                     candidate-pixel-index
                                     (inc next-free-channel-index))
              (recur (inc encrypted-message-bit-index)
                     next-random-block-counter
                     current-random-choice-block
                     next-byte-offset))))))))