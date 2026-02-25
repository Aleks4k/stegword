(ns app.core
  (:gen-class)
  (:require [seesaw.core :as s]
            [app.ui :as ui]))
(defn -main [& _]
  (s/invoke-later
   (ui/start-ui)))