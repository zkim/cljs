(ns cljs.test.core
  (:use [cljs.core] :reload)
  (:use [clojure.test])
  (:require [cljs.rhino :as rhino])
  (:import (org.mozilla.javascript Context
                                   Scriptable
                                   NativeArray
                                   NativeObject)))


;; ## Test helpers

(defn narr-to-seq [narr]
  (->> narr
    (.getIds)
    (seq)
    (map #(.get narr % nil))))

(defn obj-to-map [obj]
  (let [obj-ids (seq (.getIds obj))
        vals (map #(.get obj % nil) obj-ids)
        keys (map keyword obj-ids)]
    (apply hash-map (interleave keys vals))))

(defn eval-js [& stmt-or-stmts]
  (let [res (rhino/eval-js
             (apply str (interpose ";" (map js stmt-or-stmts))))]
    (cond
     (= NativeArray (class res)) (narr-to-seq res)
     (= NativeObject (class res)) (obj-to-map res)
     :else res)))


;; # Interesting Examples
;;
;; Start here if you'd like to get an idea of what
;; **cljs** is capable of.


;; This will (with jquery) create a `<div />`, set it's text to "Click Me!",
;; set it's dimensions to 100x100, set it's background color to red, pop up
;; an alert box, with the message "I was clicked", when clicked, and insert
;; it as a child of `<body />`.
;;
;; See the example [here](http://zkim.github.com/cljs/examples/red-clickable-box.html).
(map js '(

          (defn click-handler []
            (alert "I was clicked!"))

          (defn body [] ($ "body"))

          (defn clickable-div []
            (doto ($ "<div />")
              (.click click-handler)
              (.css {:width 100
                     :height 100
                     :backgroundColor "red"})
              (.append "Click Me!")))

          (.ready ($ document)
                  (fn []
                    (.append (body) (clickable-div))))
                   
          ))

;; cljs code is represented wherever you see `(eval-js '...`.

;; # Ops By Category

;; ## Defining Variables and Functions

(deftest test-var-definition
  (is (= "hello"
         (eval-js '(def x "hello")
                  'x))))

(deftest test-function-definition
  (is (= "hello"
         (eval-js '(defn x [] "hello")
                  '(x)))))

(deftest test-varargs
  (is (= [2 3 4])
      (eval-js '(defn x [& args]
                  (map (fn [i] (+ i 1)) args))
               '(x 1 2 3))))

(deftest test-varargs-2
  (is (= 10
         (eval-js '(defn x [a b & args]
                     (+ a b
                        (reduce (fn [col i] (+ col i)) args)))
                  '(x 1 2 3 4)))))

;; Calling functions

(deftest test-wrap-parens
  (is (= 6
         (eval-js '((fn [x y z] (+ x y z)) 1 2 3)))))

(deftest test-apply
  (is (= 6
         (eval-js '(apply + [1 2 3]))))
  (is (= 10
         (eval-js '(apply + 1 2 [3 4])))))

;; ## Conditionals

(deftest test-if
  (is (= "foo")
      (eval-js '(if true "foo" "bar")))
  (is (= "bar")
      (eval-js '(if false "foo" "bar"))))

;; ## Comparisons

(deftest test-=
  (is (eval-js '(= 1 1 1)))
  (is (not (eval-js (= 1 1 2)))))

(deftest test-<
  (is (eval-js '(< 1 2)))
  (is (eval-js '(< 1 2 3)))
  (is (not (eval-js '(< 2 1)))))

(deftest test->
  (is (eval-js '(> 2 1)))
  (is (eval-js '(> 3 2 1)))
  (is (not (eval-js '(> 1 2)))))

;; ## 'Hash Map' Ops

(deftest test-basic-map
  (is (= {:hello "world"}
         (eval-js '{:hello "world"}))))

(deftest test-merge-maps
  "Define a variable x which is the combination of {:hello \"world\"} and
   {:foo \"bar\"}, then output (:foo x), which should be \"bar\"."
  (is (= {:hello "world" :foo "bar"}
         (eval-js '(merge {:hello "world"} {:foo "bar"})))))

(deftest test-assoc
  (is (= {:hello "world" :foo "bar" :baz "bap"}
         (eval-js '(assoc {:hello "world"} :foo "bar" :baz "bap")))))

(deftest test-map-with-fn
  (is (= 5
         (eval-js '(def x {:myfn (fn [] (+ 2 3))})
                  '((:myfn x))))))

;; ## General Ops

(deftest test-map-function
  (is (= [2 3 4] (eval-js '(map (fn [x] (+ 1 x)) [1 2 3])))))

(deftest test-reduce-function
  (is (= 6 (eval-js '(reduce (fn [col val] (+ col val)) [1 2 3])))))

(deftest test-handle-println
  (is (= "console.log(\"hello world\")" (handle-println '(println "hello world")))))

;; Javascript Interop

(deftest test-new
  (is (eval-js '(String. "foo"))))

;; # Low-Level Converters

(deftest test-convert-map
  (is (= "(function(){return {'hello':\"world\"};})()" (convert-map {:hello "world"}))))

(deftest test-convert-string
  (is (= "hello world" (eval-js "hello world"))))

(deftest test-convert-number
  (is (= 5 (eval-js 5))))

(deftest test-convert-vector
  (is (= [1 2 3] (eval-js '[1 2 3]))))

(deftest test-convert-symbol
  (is (= 'hello_world (convert-symbol 'hello-world)))
  (is (= 'hello.world (convert-symbol 'hello/world))))

(deftest test-emit-function
  (is (= "(function(x,y){\n5;\n6;\nreturn 7;\n})" (emit-function '[x y] '(5 6 7)))))

(deftest test-convert-dot-function
  (is (= "x.stuff(1,2,3)" (convert-dot-function '(.stuff x 1 2 3)))))

(deftest test-convert-plain-function
  (is (= "stuff(1,2,3)" (convert-plain-function '(stuff 1 2 3)))))

(deftest test-convert-function
  (is (= "x.stuff(1,2,3)" (convert-function '(.stuff x 1 2 3))))
  (is (= "stuff(1,2,3)" (convert-function '(stuff 1 2 3)))))

(deftest test-handle-if
  (is (= "(function(){if((x == 1)){\n return x;\n}})()" (handle-if '(if (= x 1) x)))))

(comment

  (js-form '(fn [x] [1 2 x 3]))

  (js-form '(def x (fn [] (println "hi"))))

  (js-form '(defn hello [a b]
              (println a)
              (println b)))

  (js-form '(defn h1 [x]
              (println "HI!")
              (str "<h1>" x "</h1>")))

  (println (js-form '(fn [x y] (println "hello world"))))

  (println (js-form '(println "hello world")))

  (js-form '(println ($ "#")))

  (js-form '($ "#hello world"))

  (println (js-form '{:hello "world"
                      :stuff (fn [x] (println "hi"))
                      :yo "hi"}))

  (println (js-form '(.click ($ "#body")
                             (fn [] (println "on click"))
                             (fn [x] (alert x)))))

  (println (js-form '(.ajax $ {:success (fn [res] (println res))
                               :failure (fn [res] (println "ERROR!"))})))

  (js-form '(let [x 5]
              (println x)))

  (spit "./resources/public/js/jsclj-test.js"
        (js-form
         '(.ready ($ document)
                  (fn []
                    (println "hi")
                    (.click ($ "body")
                            (fn []
                              (alert "hi")))))))

  (js-form
   '(.ready ($ document)
            (fn [] (println "hi"))))

  (js-form
   '(.ready ($ document)
            (fn [] (println "hi"))))

  (def x '($ "#hi"))

  (js-form x)

  (js-form '(defn onready []
              (doto ($ "body")
                  (.css {:backgroundColor "red"})
                  (.append ($ "<h1>HI!</h1>")))))

  (js-form '(dostuff (doto ($ "<div />")
                         (.append ($ "<span />")))))

  (js-form '(defn log-stuff [s] (println [1 2 s])))

  (js-form '[1 2 3])

  (doc read)

  (js-form '(defn add-5 [x]
              (+ 5 x)
              (add-5 5)))


  (compile-cljjs-to "./resources/public/cljjs/test.clj.js" "./resources/public/js/test.js")

)