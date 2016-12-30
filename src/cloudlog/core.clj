(ns cloudlog.core)

(defmacro --> [rulename source & conds]
  (let [funcname (symbol (str (name rulename) "-0"))]
    `(do (defn ~funcname [[~@(rest source)]] [[~@(rest (first conds))]]))))
