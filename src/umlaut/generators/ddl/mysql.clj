(ns umlaut.generators.ddl.mysql
  (:require [camel-snake-kebab.core :refer [->snake_case]]
            [umlaut.core :as u]
            [umlaut.utils :as util]
            [clojure.string :as str]
            [weavejester.dependency :as dep]))

(def space "lang/ddl")

(defn annotations-by-space [annotations]
  "Filter an array of annotation by key and value"
  (filter #(= space (% :space)) annotations))

(defn annotations-by-space-key [key annotations]
  "Filter an array of annotation by key and value"
  (filter #(and (= space (% :space)) (= key (% :key))) annotations))

(def type-id->column-type
  {"ID" "BIGINT"
   "String" "TEXT"
   "Integer" "INT"})

(defn enums-types [umlaut]
  (->> umlaut
       :nodes
       (filter (comp #(= :enum %) first second))
       (map (comp (juxt :id (fn [def] (str "ENUM("
                                           (str/join ","
                                                     (map #(str "'"
                                                                %
                                                                "'")
                                                          (:values def))
                                                     )
                                           ")")))
                  second
                  second))
       (into {})))

(defn map-column-type [field-def umlaut]
  (when-not (seq (some->> (get-in field-def [:field-annotations :others])
                          (annotations-by-space-key "supress")))
    (or (let [type-annot-val (some->> (get-in field-def [:field-annotations :others])
                                      (annotations-by-space-key "type")
                                      first
                                      :value)]
          (when type-annot-val
            (let [[t len] (str/split type-annot-val #"\.")]
              (if len
                (str t "(" len ")")
                t))))
        (get (merge type-id->column-type (enums-types umlaut))
             (get-in field-def [:return :type-id])))))

(defn render-primary-key-stmt [field-defs]
  (let [pk-annots (->> field-defs
                       (map (juxt (comp ->snake_case :id)
                                  (comp seq
                                        (partial annotations-by-space-key "primary_key")
                                        :others
                                        :field-annotations)))
                       (filter (comp seq second)))]
    (when (seq pk-annots)
      (str "   ";;For nice indentation
           "PRIMARY KEY("
           (->> pk-annots
                (map first)
                (str/join ","))
           ")"))))

(defn render-unique-key-stmt [field-defs]
  (let [unique-annots (->> field-defs
                           (map (juxt (comp ->snake_case :id)
                                      (comp seq
                                            (partial annotations-by-space-key "unique")
                                            :others
                                            :field-annotations)))
                           (filter (comp seq second)))]
    (when (seq unique-annots)
      (str "   ";;For nice indentation
           "UNIQUE INDEX unique_"
           (->> unique-annots
                (map first)
                (str/join "_"))
           "("
           (->> unique-annots
                (map first)
                (str/join ","))
           ")"))))

(defn render-foreign-key-stmt [def field-defs]
  (let [fk-annots (->> field-defs
                       (map (juxt (comp ->snake_case :id)
                                  (comp #(when % (str/split % #"\."))
                                        :value
                                        first
                                        (partial annotations-by-space-key "fk")
                                        :others
                                        :field-annotations)))
                       (filter (comp seq second)))]
    (when (seq fk-annots)
      (->> fk-annots
           (map (fn [[fk-col [parent-table parent-col]]]
                  (str "   ";;For nice indentation
                       "CONSTRAINT `fk_"
                       (->snake_case (:id def))
                       "_"
                       (->snake_case parent-table)
                       "` "
                       "FOREIGN KEY (" fk-col ") REFERENCES " (->snake_case parent-table) " (" (->snake_case parent-col) ") " )))))))

(defn column-statements [umlaut field-defs]
  (->> field-defs
       (keep (fn [{:as field-def
                   :keys [id return field-annotations]}]
               (let [column-type (map-column-type field-def umlaut)]
                 (when column-type

                   (str "   ";;For nice indentation
                        (->snake_case id)
                        " "
                        column-type
                        " "
                        (when (:required return)
                          "NOT NULL")
                        " "
                        (some->> field-annotations
                                 :others
                                 (filter #(and (= (:space %) "lang/ddl")
                                               (#{"default_value"} (:key %))))
                                 first
                                 :value
                                 (str " DEFAULT "))
                        " "
                        (when-let [default-fn (->> field-annotations
                                                   :others
                                                   (filter #(and (= (:space %) "lang/ddl")
                                                                 (#{"default_fn"} (:key %))))
                                                   first)]
                          (str "DEFAULT " (:value default-fn) "()"))
                        " "
                        (when-let [annots (->> field-annotations
                                               :others
                                               (filter #(and (= (:space %) "lang/ddl")
                                                             (#{"AUTO_INCREMENT"}
                                                              (:key %))
                                                             (= (:value %) "true")))
                                               seq)]
                          (->> annots
                               (map :key)
                               (str/join " ")))
                        " "
                        (when-let [doc (:documentation field-annotations)]
                          (str "comment '" doc "'")))))))))

(defn umlaut-def->create-table+drop-table [def umlaut]
  (let [pk-stmt (render-primary-key-stmt (:fields def))
        unique-stmt (render-unique-key-stmt (:fields def))
        fk-stmt (render-foreign-key-stmt def (:fields def))
        col-stmts (column-statements umlaut (:fields def))]
    {:drop (str "DROP TABLE " (->snake_case (:id def)))
     :create
     (str/join ["CREATE TABLE "
                (->snake_case (:id def))
                " (\n"
                (str/join ",\n"
                          (->> (into (conj (vec col-stmts)
                                           pk-stmt
                                           unique-stmt)
                                     fk-stmt)
                               (remove nil?)))
                ")"
                "\nENGINE = InnoDB"
                (when-let [comment (:value (first (filter #(= (:space %) :documentation) (:annotations def))))]
                  (str "\n" "comment=" "'" comment "'"))])}))

(defn topological-orders [umlaut-nodes]
  (let [topo-sorted (dep/topo-sort
                     (loop [g (dep/graph)
                            [[type field-types :as head] & rst]
                            (->> umlaut-nodes
                                 (map
                                  (juxt first
                                        (comp
                                         #(filter (complement util/primitive?) %)
                                         #(map (comp :type-id :return) %)
                                         :fields
                                         second
                                         second)))
                                 (filter (comp seq second)))]
                       (if head
                         (recur (reduce (fn [acc t]
                                          (dep/depend acc type t))
                                        g
                                        field-types)
                                rst)
                         g)))]
    (->> topo-sorted
         (map-indexed (fn [idx id] {id idx}))
         (into {}))))

(defn gen [f]
  (let [umlaut (u/run f)
        umlaut-nodes (:nodes umlaut)
        topo-orders (topological-orders umlaut-nodes)]
    (->> umlaut-nodes
         (sort-by #(get topo-orders (first %)))
         (keep (fn [[id [type definition]]]
                 (let [ddl-annotations (seq (annotations-by-space
                                             (:annotations definition)))]
                   (when ddl-annotations
                     (umlaut-def->create-table+drop-table definition umlaut)))))
         reverse)))

(comment
  (println
   (:create (second (gen "datamodel/datamodel.umlaut"))))


  )
