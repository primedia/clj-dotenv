;; src/dotenv/core.clj

;; Clojure implimentation of the doteng Ruby Gem.

(ns ^{:author "Jack Morrill"
      :doc "Load environment variable definitions from .env{.environment}, .env.local files into the JVM System Properties."}
      com.rentpath.dotenv.core
      (:require [clojure.java.io :as io      ]
                [clojure.string  :as string  ]
                [com.rentpath.environs.core :as environs :refer :all]
                [me.raynes.fs    :as fs      ]))

(def ^{:doc "An environment variable that specifies which environment we're running in."}
  +dot-env-var+
  "ENVIRONMENT")

(def +env-config-files+
  {"ci"          ".ci"
   "development" ".development"
   "test"        ".test"
   "acceptance"  ".acceptance"
   "production"  ".production"
   "qa"          ".qa"
   "stage"       ".stage"})

(defn exists?
  "Returns true if file exists and is a regular file, else returns false."
  [filename]
  (.isFile (io/file filename)))

(defn- get-env
  [varname]
  (get (System/getenv) varname))

(defn make-filename
  ([directory]
     (let [env (get-env +dot-env-var+)]
       (->> (str ".env" (.toLowerCase (get +env-config-files+ env "")))
            (vector directory)
            (string/join (System/getProperty "file.separator"))
            (fs/expand-home))))
  ([]
     (make-filename (System/getenv "PWD"))))

(defn set-property!
  [k v]
  (when (and k v)
    (System/setProperty k v)))

(defn load-env
  "Load environment variable definitions from .env{.ENVIRONMENT} into a map."
  ([]
     (load-env (make-filename)))
  ([config-filename]
     (if (exists? config-filename)
       (with-open [file (io/reader config-filename)]
         (->> (line-seq file)
              (map #(string/replace % #"(^export\s+)|([#].*)" ""))
              (map string/trim)
              (remove string/blank?)
              (map #(string/replace % #"['\"]" ""))
              (map #(string/split % #"(\s*=\s*)|(:\s+)"))
              (into {})))
       (throw (Error. (format "Could not load configuration file: %s" config-filename))))))

(defn dotenv!
  "Create JVM System Properties from environment variables defined in .env{.ENVIRONMENT}.
   If .env.local exists, load those JVM System Properties too, overriding definitions from .env{.environment}.

  .env.* file format:

    foo=1
    bar='hairy URL'

  YAML-like format:

    foo: 1
    bar: 'hairy URL'

  For convienence, a BASH Shell format is accepted:

    export foo=1
    export bar='gnarley URL'

  Blank lines and all characters after a comment character (#) in all lines are ignored.  
  "
  []
  (let [environ (load-env)]
    (doseq [[k v] environ] (set-property! k v)))
    (if (exists? ".env.local")
      (let [environ-local (load-env ".env.local")]
        (doseq [[k v] environ-local] (set-property! k v)))))