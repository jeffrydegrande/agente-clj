(ns agente.core-test
  (:require [clojure.test :refer :all]
            [agente.core :as core]))


(deftest sanity-check
  (is (= 1 1)))


(deftest main-test
  (is (string? (with-out-str (core/-main)))))
