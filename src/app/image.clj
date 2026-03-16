(ns app.image
  (:require [clojure.java.io :as io])
  (:import
   (java.awt.image BufferedImage DataBufferInt)
   (javax.imageio ImageIO)))
;; ------------------------------------------------------------
;; Image loading / saving helpers
;; ------------------------------------------------------------
(defn calculate-image-capacity-in-bits
  "Image capacity in bits when using 1 bit from each RGB channel.
   Capacity = width * height * 3"
  [path]
  (let [image (ImageIO/read (io/file path))]
    ;; ImageIO/read can still return nil in some edge cases,
    ;; even if validation succeeded earlier.
    (if (nil? image)
      (throw (Exception. "Could not read image."))
      (* (.getWidth image) (.getHeight image) 3))))
(defn build-encrypted-output-path
  "Create output file path by appending _encrypted before .png"
  [path]
  (let [input-file (io/file path)
        file-name (.getName input-file)
        dot-index (.lastIndexOf file-name ".")
        base-name (if (neg? dot-index) file-name (.substring file-name 0 dot-index))
        parent-directory (or (.getParent input-file) ".")]
    (.getPath (io/file parent-directory (str base-name "_encrypted.png")))))
(defn load-image-as-rgb
  "Always return the image as TYPE_INT_RGB.
   This guarantees stable DataBufferInt access and matches our RGB-only algorithm."
  [path]
  (let [source-image (ImageIO/read (io/file path))
        image-width (.getWidth source-image)
        image-height (.getHeight source-image)
        rgb-image (BufferedImage. image-width image-height BufferedImage/TYPE_INT_RGB)
        graphics-context (.createGraphics rgb-image)]
    (try
      (.drawImage graphics-context source-image 0 0 nil)
      (finally
        (.dispose graphics-context)))
    rgb-image))
(defn get-image-pixels
  "Return the raw int[] pixel array from a TYPE_INT_RGB image."
  [^BufferedImage image]
  (let [image-data-buffer ^DataBufferInt (.getDataBuffer (.getRaster image))]
    (.getData image-data-buffer)))
(defn save-png!
  "Save image as PNG to the given path."
  [^BufferedImage image output-path]
  (ImageIO/write image "png" (io/file output-path)))