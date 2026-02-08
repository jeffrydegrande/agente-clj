(ns agente.tools.core)

(defmacro def-tool
  [name {:keys [description parameters]} args & body]
  `(def ~name
     {:name ~(str name)
      :description ~description
      :parameters ~parameters
      :handler (fn ~args ~@body)}))
