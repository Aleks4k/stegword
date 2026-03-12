(ns app.actions)
(defn encrypt! [path password message]
  (println "Encrypting" path "with password" password "and message" message)
  :ok)